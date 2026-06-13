package com.example.silero.audio

/**
 * 变声器：对整段 PCM 应用 [VoiceParams]。
 * 每次处理新建 SoundTouch 实例，避免上一句的残留样本影响下一句。
 */
class VoiceChanger(private val sampleRate: Int = 16000) {

    fun apply(pcm: ShortArray, params: VoiceParams): ShortArray {
        if (params.isIdentity || pcm.isEmpty()) return pcm

        val st = SoundTouch()
        return try {
            st.setSampleRate(sampleRate)
            st.setChannels(1)
            st.setPitchSemiTones(params.pitchSemiTones)
            st.setTempo(params.tempo)
            st.process(pcm)
        } finally {
            st.release()
        }
    }
}
