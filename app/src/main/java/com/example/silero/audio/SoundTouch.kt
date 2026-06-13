package com.example.silero.audio

/**
 * SoundTouch JNI 封装。在内存中对 16-bit PCM short 数组做音高 / 语速 / 采样率变换。
 *
 * 用法：
 *   val st = SoundTouch()
 *   st.setSampleRate(16000); st.setChannels(1)
 *   st.setPitchSemiTones(5f)
 *   val out = st.process(pcm)
 *   st.release()
 */
class SoundTouch {

    private var handle: Long = 0

    init {
        handle = newInstance()
    }

    fun setSampleRate(rate: Int) = setSampleRate(handle, rate)
    fun setChannels(channels: Int) = setChannels(handle, channels)

    /** 语速（不改音高），1.0 = 原速 */
    fun setTempo(tempo: Float) = setTempo(handle, tempo)

    /** 整体速率（同时改音高+速度），1.0 = 原始 */
    fun setRate(rate: Float) = setRate(handle, rate)

    /** 音高，单位半音。正=升高，负=降低 */
    fun setPitchSemiTones(semitones: Float) = setPitchSemiTones(handle, semitones)

    /** 处理整段 PCM，返回处理后的 PCM */
    fun process(input: ShortArray): ShortArray {
        if (handle == 0L) return ShortArray(0)
        return process(handle, input)
    }

    fun release() {
        if (handle != 0L) {
            deleteInstance(handle)
            handle = 0
        }
    }

    // --- native ---
    private external fun newInstance(): Long
    private external fun deleteInstance(handle: Long)
    private external fun setSampleRate(handle: Long, rate: Int)
    private external fun setChannels(handle: Long, channels: Int)
    private external fun setTempo(handle: Long, tempo: Float)
    private external fun setRate(handle: Long, rate: Float)
    private external fun setPitchSemiTones(handle: Long, semitones: Float)
    private external fun process(handle: Long, input: ShortArray): ShortArray

    companion object {
        init {
            System.loadLibrary("soundtouch_jni")
        }
    }
}
