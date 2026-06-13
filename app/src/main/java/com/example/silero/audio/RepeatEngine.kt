package com.example.silero.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 复读引擎：持续录音 → Silero VAD 断句 → 检测到「说完一句」后变声回放。
 *
 * 工作流程（单后台线程）：
 *   1. AudioRecord 采集 16kHz 单声道 PCM
 *   2. 每 512 采样喂给 SileroVad，得到语音概率
 *   3. 概率 > 进入阈值 → 标记说话开始，开始缓存
 *   4. 概率持续低于退出阈值超过 [silenceTailMs] → 判定说完
 *   5. 把缓存的整句 PCM 交给 VoiceChanger 变声，再用 AudioPlayer 回放
 *   6. 回放期间暂停采集，播完继续监听
 */
class RepeatEngine(
    context: Context,
    private val sampleRate: Int = 16000,
) {
    interface Listener {
        fun onStateChanged(state: EngineState)
        fun onSpeechProbability(prob: Float)
    }

    enum class EngineState { IDLE, LISTENING, SPEAKING, PROCESSING, PLAYING }

    // 可调端点检测参数
    @Volatile var enterThreshold: Float = 0.4f
    @Volatile var exitThreshold: Float = 0.3f
    @Volatile var silenceTailMs: Int = 700
    @Volatile var minSpeechMs: Int = 250

    /** 麦克风灵敏度：喂 VAD 前的输入放大倍数。越大越灵敏（华为录音电平低，默认 12）。 */
    @Volatile var vadInputGain: Float = 12f

    /** 延迟播放：说完一句后等待多少毫秒再回放（默认 0，不延迟）。 */
    @Volatile var playbackDelayMs: Int = 0

    @Volatile var voiceParams: VoiceParams = VoicePreset.ORIGINAL.params

    private val vad = SileroVad(context, sampleRate)
    private val changer = VoiceChanger(sampleRate)
    private val player = AudioPlayer(sampleRate)

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    var listener: Listener? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        job = scope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        player.stop()
        notifyState(EngineState.IDLE)
    }

    fun release() {
        stop()
        vad.close()
    }

    private fun notifyState(s: EngineState) {
        listener?.onStateChanged(s)
    }

    @SuppressLint("MissingPermission")
    private suspend fun runLoop() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val recordBufSize = maxOf(minBuf, SileroVad.WINDOW_SIZE * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recordBufSize
        )

        val frameShort = ShortArray(SileroVad.WINDOW_SIZE)
        val frameFloat = FloatArray(SileroVad.WINDOW_SIZE)

        // 当前句子的累积缓冲
        val speech = ArrayList<Short>(sampleRate * 5)
        var inSpeech = false
        var silenceAccumMs = 0
        var speechMs = 0
        val frameMs = SileroVad.WINDOW_SIZE * 1000 / sampleRate  // 32ms

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 初始化失败 state=${recorder.state}，无法录音")
            recorder.release()
            notifyState(EngineState.IDLE)
            return
        }

        var frameCount = 0L
        var maxProbSinceLog = 0f

        try {
            recorder.startRecording()
            vad.reset()
            notifyState(EngineState.LISTENING)
            Log.i(TAG, "开始录音循环，frameMs=$frameMs enter=$enterThreshold exit=$exitThreshold tail=${silenceTailMs}ms")

            while (isActiveLoop()) {
                val read = readFully(recorder, frameShort)
                if (read < SileroVad.WINDOW_SIZE) continue

                // short -> float，算本帧峰值（诊断用，原始电平）。
                // 华为录音电平被压得很低，喂 VAD 前先放大 vadInputGain 倍（带削顶），
                // 否则正常/偏小声说话概率到不了阈值。捕获用的 frameShort 仍是原始数据。
                val gain = vadInputGain
                var peak = 0
                for (i in frameShort.indices) {
                    val s = frameShort[i].toInt()
                    val a = if (s < 0) -s else s
                    if (a > peak) peak = a
                    var v = (frameShort[i] / 32768f) * gain
                    if (v > 1f) v = 1f
                    if (v < -1f) v = -1f
                    frameFloat[i] = v
                }
                val prob = vad.process(frameFloat)
                listener?.onSpeechProbability(prob)

                // 每 ~1 秒打一行诊断：麦克风峰值 + VAD 最高概率
                if (prob > maxProbSinceLog) maxProbSinceLog = prob
                frameCount++
                if (frameCount % 31 == 0L) {
                    Log.i(TAG, "诊断 micPeak=$peak(0-32767) vadProbMax=${"%.3f".format(maxProbSinceLog)} inSpeech=$inSpeech")
                    maxProbSinceLog = 0f
                }

                if (!inSpeech) {
                    if (prob >= enterThreshold) {
                        inSpeech = true
                        silenceAccumMs = 0
                        speechMs = 0
                        speech.clear()
                        appendFrame(speech, frameShort)
                        speechMs += frameMs
                        notifyState(EngineState.SPEAKING)
                        Log.i(TAG, "检测到说话开始 prob=${"%.3f".format(prob)}")
                    }
                } else {
                    appendFrame(speech, frameShort)
                    speechMs += frameMs
                    if (prob < exitThreshold) {
                        silenceAccumMs += frameMs
                        if (silenceAccumMs >= silenceTailMs) {
                            // 说完一句
                            inSpeech = false
                            val effectiveMs = speechMs - silenceAccumMs
                            Log.i(TAG, "检测到说完一句 时长=${effectiveMs}ms 采样=${speech.size}")
                            if (effectiveMs >= minSpeechMs) {
                                handleUtterance(recorder, speech.toShortArray())
                                vad.reset()
                            } else {
                                Log.i(TAG, "句子太短(<${minSpeechMs}ms)，忽略")
                            }
                            speech.clear()
                            notifyState(EngineState.LISTENING)
                        }
                    } else {
                        silenceAccumMs = 0
                    }
                }
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
        }
    }

    /** 变声 + 回放，期间暂停采集线程。 */
    private suspend fun handleUtterance(recorder: AudioRecord, pcm: ShortArray) {
        notifyState(EngineState.PROCESSING)
        // 暂停录音避免把自己的回放再录进去
        try { recorder.stop() } catch (_: Exception) {}

        val processed = withContext(Dispatchers.Default) {
            changer.apply(pcm, voiceParams)
        }
        Log.i(TAG, "变声完成 输入采样=${pcm.size} 输出采样=${processed.size} params=$voiceParams")

        // 延迟播放：说完后等待设定时长再回放
        val delayMs = playbackDelayMs
        if (delayMs > 0) {
            Log.i(TAG, "延迟播放 ${delayMs}ms")
            delay(delayMs.toLong())
        }

        notifyState(EngineState.PLAYING)
        withContext(Dispatchers.Default) {
            player.play(processed)
        }
        Log.i(TAG, "回放结束，恢复监听")

        // 恢复录音
        try {
            recorder.startRecording()
        } catch (_: Exception) {}

        // 自适应丢弃回声：读帧喂 VAD（只更新状态、不触发检测），直到外放回声/混响
        // 真正衰减——概率连续低于退出阈值约 300ms，或达到上限 1.5s 才重新监听。
        // 这实现严格半双工：回放时不检测，且回放余响散尽前不重新武装。
        val frameMs = SileroVad.WINDOW_SIZE * 1000 / sampleRate
        val tmpS = ShortArray(SileroVad.WINDOW_SIZE)
        val tmpF = FloatArray(SileroVad.WINDOW_SIZE)
        val needQuietMs = 300
        val maxDrainMs = 1500
        var quietMs = 0
        var elapsed = 0
        while (isActiveLoop() && quietMs < needQuietMs && elapsed < maxDrainMs) {
            if (readFully(recorder, tmpS) < SileroVad.WINDOW_SIZE) break
            for (i in tmpS.indices) {
                var v = (tmpS[i] / 32768f) * vadInputGain
                if (v > 1f) v = 1f
                if (v < -1f) v = -1f
                tmpF[i] = v
            }
            val p = vad.process(tmpF)
            elapsed += frameMs
            if (p < exitThreshold) quietMs += frameMs else quietMs = 0
        }
        Log.i(TAG, "回放后回声衰减完成 elapsed=${elapsed}ms，恢复监听")

        notifyState(EngineState.LISTENING)
    }

    private fun isActiveLoop(): Boolean = job?.isActive ?: false

    private fun readFully(recorder: AudioRecord, buf: ShortArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = recorder.read(buf, off, buf.size - off)
            if (n <= 0) return off
            off += n
        }
        return off
    }

    private fun appendFrame(dst: ArrayList<Short>, frame: ShortArray) {
        for (s in frame) dst.add(s)
    }

    companion object {
        private const val TAG = "RepeatEngine"
    }
}
