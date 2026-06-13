package com.example.silero

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.silero.audio.RepeatEngine
import com.example.silero.audio.VoiceParams
import com.example.silero.audio.VoicePreset

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = RepeatEngine(app.applicationContext)

    var engineState by mutableStateOf(RepeatEngine.EngineState.IDLE)
        private set
    var speechProb by mutableFloatStateOf(0f)
        private set

    var preset by mutableStateOf(VoicePreset.ORIGINAL)
        private set

    // 可调参数
    var pitch by mutableFloatStateOf(VoicePreset.ORIGINAL.params.pitchSemiTones)
        private set
    var tempo by mutableFloatStateOf(VoicePreset.ORIGINAL.params.tempo)
        private set

    var silenceTailMs by mutableFloatStateOf(DEFAULT_SILENCE_TAIL_MS)
        private set

    // 麦克风灵敏度（VAD 输入放大倍数），默认 12，范围 1~30
    var micSensitivity by mutableFloatStateOf(DEFAULT_MIC_SENSITIVITY)
        private set

    // 延迟播放（毫秒），默认 0，范围 0~3000
    var playbackDelayMs by mutableFloatStateOf(DEFAULT_PLAYBACK_DELAY_MS)
        private set

    val isRunning: Boolean get() = engine.isRunning

    init {
        engine.listener = object : RepeatEngine.Listener {
            override fun onStateChanged(state: RepeatEngine.EngineState) {
                engineState = state
            }
            override fun onSpeechProbability(prob: Float) {
                speechProb = prob
            }
        }
        applyParams()
    }

    fun toggle() {
        if (engine.isRunning) engine.stop() else engine.start()
    }

    fun selectPreset(p: VoicePreset) {
        preset = p
        pitch = p.params.pitchSemiTones
        tempo = p.params.tempo
        applyParams()
    }

    fun updatePitch(v: Float) {
        pitch = v
        applyParams()
    }

    fun updateTempo(v: Float) {
        tempo = v
        applyParams()
    }

    fun updateSilenceTail(v: Float) {
        silenceTailMs = v
        engine.silenceTailMs = v.toInt()
    }

    fun updateMicSensitivity(v: Float) {
        micSensitivity = v
        engine.vadInputGain = v
    }

    fun updatePlaybackDelay(v: Float) {
        playbackDelayMs = v
        engine.playbackDelayMs = v.toInt()
    }

    /** 恢复默认：音高/语速回到当前预设，断句静音 700ms，灵敏度 12x，延迟 0。 */
    fun resetDefaults() {
        pitch = preset.params.pitchSemiTones
        tempo = preset.params.tempo
        silenceTailMs = DEFAULT_SILENCE_TAIL_MS
        micSensitivity = DEFAULT_MIC_SENSITIVITY
        playbackDelayMs = DEFAULT_PLAYBACK_DELAY_MS
        engine.silenceTailMs = DEFAULT_SILENCE_TAIL_MS.toInt()
        engine.vadInputGain = DEFAULT_MIC_SENSITIVITY
        engine.playbackDelayMs = DEFAULT_PLAYBACK_DELAY_MS.toInt()
        applyParams()
    }

    private fun applyParams() {
        engine.voiceParams = VoiceParams(pitchSemiTones = pitch, tempo = tempo)
    }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_SILENCE_TAIL_MS = 700f
        private const val DEFAULT_MIC_SENSITIVITY = 12f
        private const val DEFAULT_PLAYBACK_DELAY_MS = 0f
    }
}
