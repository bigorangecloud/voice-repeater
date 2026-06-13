package com.example.silero.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/** 用 AudioTrack 播放 16-bit 单声道 PCM。回放前做峰值归一化，保证微弱录音也能听见。 */
class AudioPlayer(private val sampleRate: Int = 16000) {

    companion object {
        private const val TAG = "AudioPlayer"
        private const val TARGET_PEAK = 26000f   // 目标峰值（满量程 32767 的 ~0.8）
        private const val MAX_GAIN = 60f         // 最大放大倍数，防止把纯噪声放大爆音
    }

    @Volatile
    private var track: AudioTrack? = null

    /** 阻塞式播放整段 PCM（应在后台线程调用）。 */
    fun play(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        stop()

        val out = normalize(pcm)

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, out.size * 2)

        val at = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            bufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track = at
        at.setVolume(AudioTrack.getMaxVolume())
        at.play()

        var offset = 0
        while (offset < out.size && track === at) {
            val written = at.write(out, offset, out.size - offset)
            if (written <= 0) break
            offset += written
        }

        // 等播放头真正把所有帧播完，再停止（避免流模式下尾部被截断）
        val totalFrames = out.size
        var guard = 0
        while (track === at && at.playbackHeadPosition < totalFrames && guard < 2000) {
            try { Thread.sleep(5) } catch (_: InterruptedException) { break }
            guard++
        }

        try {
            at.stop()
            at.release()
        } catch (_: Exception) {}
        if (track === at) track = null
    }

    /** 按峰值归一化放大到 [TARGET_PEAK]，最大放大 [MAX_GAIN] 倍。 */
    private fun normalize(pcm: ShortArray): ShortArray {
        var peak = 1
        for (s in pcm) {
            val a = if (s < 0) -s.toInt() else s.toInt()
            if (a > peak) peak = a
        }
        var gain = TARGET_PEAK / peak
        if (gain > MAX_GAIN) gain = MAX_GAIN
        if (gain < 1f) gain = 1f   // 已经够大就不缩小
        Log.i(TAG, "回放归一化 原始峰值=$peak 增益=${"%.1f".format(gain)}x 采样=${pcm.size}")

        if (gain == 1f) return pcm
        val out = ShortArray(pcm.size)
        for (i in pcm.indices) {
            var v = (pcm[i] * gain).toInt()
            if (v > 32767) v = 32767
            if (v < -32768) v = -32768
            out[i] = v.toShort()
        }
        return out
    }

    fun stop() {
        track?.let {
            try {
                it.pause()
                it.flush()
                it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        track = null
    }
}
