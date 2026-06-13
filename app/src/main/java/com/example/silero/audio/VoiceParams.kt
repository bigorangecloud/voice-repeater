package com.example.silero.audio

/**
 * 变声参数（均为 SoundTouch 原生支持，可独立调节）。
 *
 * 说明：SoundTouch 的音高移位基于「重采样 + 时间拉伸」，共振峰会随音高一起移动——
 * 这正是男声/女声/婴儿声听感差异的来源（纯基频移动而共振峰不动反而不自然）。
 * 因此本应用用「音高 + 语速」两个真实可控的维度来塑造音色。
 *
 * - pitchSemiTones: 音高移位（半音）。+ 升高，- 降低。范围约 [-12, 12]。
 * - tempo: 语速倍率。1.0 = 原速，>1 更快，<1 更慢。范围约 [0.5, 2.0]。
 */
data class VoiceParams(
    val pitchSemiTones: Float = 0f,
    val tempo: Float = 1.0f,
) {
    val isIdentity: Boolean
        get() = pitchSemiTones == 0f && tempo == 1.0f
}

/** 预设音色。 */
enum class VoicePreset(val label: String, val params: VoiceParams) {
    ORIGINAL("原声", VoiceParams()),
    MALE("男声", VoiceParams(pitchSemiTones = -5f, tempo = 0.97f)),
    FEMALE("女声", VoiceParams(pitchSemiTones = 5f, tempo = 1.0f)),
    BABY("婴儿声", VoiceParams(pitchSemiTones = 9f, tempo = 1.06f)),
}
