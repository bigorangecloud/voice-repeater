# Silero 变声复读机

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![License](https://img.shields.io/badge/license-MIT-blue)
![minSdk](https://img.shields.io/badge/minSdk-24-orange)

[English](README.md) | **简体中文**

一个 Android App：你说一句话，应用通过 **Silero VAD** 检测到你说完后，自动把这句话**变声复读**出来。支持原声 / 男声 / 女声 / 婴儿声预设，音高、语速、断句灵敏度、麦克风灵敏度、延迟播放均可调。

## 功能

- 🎙️ **实时端点检测**：基于 Silero VAD（ONNX），自动判断「说完一句」
- 🗣️ **变声复读**：检测到整句后用 SoundTouch 做音高/语速变换并回放
- 🎚️ **音色预设**：原声 / 男声 / 女声 / 婴儿声一键切换
- 🎛️ **可调参数**（滑竿，实时生效）：
  - 音高（-12 ~ +12 半音）
  - 语速（0.5x ~ 2.0x）
  - 断句静音时长（300 ~ 1500 ms）
  - 麦克风灵敏度（VAD 输入放大 1x ~ 30x，适配不同机型录音电平）
  - 延迟播放（0 ~ 3000 ms）
- 🔁 **一键恢复默认**
- 🔇 **严格半双工**：回放时不检测，回放后等回声衰减才重新监听，避免外放自激循环

## 工作原理

```
麦克风 → AudioRecord(16kHz/单声道/PCM16)
       → Silero VAD (ONNX, 每 512 采样=32ms 输出语音概率)
       → 端点检测 (进入阈值/退出阈值/尾部静音判定"说完")
       → 缓存整句 PCM
       → SoundTouch 变声 (音高+语速移位, JNI/C++)
       → AudioTrack 回放
```

回放期间会暂停录音，避免把自己的回放再录进去。

## 截图

<p align="center">
  <img src="docs/screenshot-main.png" alt="主界面" width="300">
</p>

## 下载

预编译 APK 发布在 [Releases](https://github.com/bigorangecloud/voice-repeater/releases) 页面。也可以自行构建（见 [构建](#构建)）。

## 模块

| 文件 | 职责 |
|------|------|
| `audio/SileroVad.kt` | Silero VAD v5 ONNX 推理封装 |
| `audio/RepeatEngine.kt` | 录音 + VAD 端点检测 + 触发变声回放的核心循环 |
| `audio/VoiceChanger.kt` | 对整段 PCM 应用变声参数 |
| `audio/SoundTouch.kt` | SoundTouch JNI 的 Kotlin 封装 |
| `audio/VoiceParams.kt` | 变声参数与预设音色 |
| `audio/AudioPlayer.kt` | AudioTrack PCM 回放 |
| `cpp/soundtouch_jni.cpp` | 自定义 JNI：内存中处理 PCM short 数组 |
| `cpp/soundtouch/` | SoundTouch 核心 C++ 源码（官方 codeberg 镜像） |
| `MainViewModel.kt` / `*.kt(UI)` | Compose 界面与状态 |

## 关于"变声"的技术说明

SoundTouch 的音高移位基于「重采样 + 时间拉伸」，共振峰会随音高一起移动——这正是男/女/婴儿声听感差异的自然来源，因此本应用用**音高(半音) + 语速**两个真实可控维度塑造音色，而非伪造独立的共振峰移位。

## 系统要求与权限

- Android 7.0 (API 24) 及以上
- 运行时需授予 **麦克风权限**（`RECORD_AUDIO`）——用于录音检测语音

## 构建

依赖本机：Android SDK（含 NDK 26.x、CMake 3.22.1、platform-34）。

```powershell
# 直接命令行构建（JAVA_HOME 指向 JDK 17+）
.\gradlew.bat assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk
```

或用 Android Studio 直接打开本目录，等待 Gradle sync 后点 Run。

## 已知限制

- **不同机型录音电平差异大**：部分机型（如华为/荣耀系）系统降噪会把录音电平压得很低，默认灵敏度下可能检测不灵敏。如遇「说话没反应」，调高「麦克风灵敏度」滑竿即可；电平正常的机型可调低以减少误触发。
- **外放可能产生回声**：音量较大时，外放的回放声会被麦克风重新拾取。应用已做半双工与回声衰减处理，但**戴耳机**能从物理上彻底消除该耦合，体验最干净。
- 变声基于音高+语速变换，非独立共振峰建模，极端参数下音质会下降。

## 贡献

欢迎 Issue 和 PR。建议流程：

1. Fork 本仓库并新建分支（`feature/xxx` 或 `fix/xxx`）
2. 保持代码风格与现有一致（Kotlin 官方风格 + Compose）
3. 在真机上验证录音/检测/回放链路正常
4. 提交 PR 并简述改动与测试情况

## 致谢

- [Silero Team](https://github.com/snakers4/silero-vad) —— 高质量、轻量的开源语音活动检测模型
- [Olli Parviainen / SoundTouch](https://codeberg.org/soundtouch/soundtouch) —— 开源音高/语速变换库

## 许可证

本项目主体采用 **MIT License**，详见 [LICENSE](LICENSE)。

> ⚠️ **第三方许可证注意**：`app/src/main/cpp/soundtouch/` 目录下的 SoundTouch 源码采用 **LGPL v2.1** 许可，**不属于** MIT 范围，其版权归原作者所有。如果你分发本应用或其衍生作品，需遵守 LGPL 的相应义务（保留版权与许可声明、允许最终用户替换该库等）。Silero VAD 模型遵循其上游仓库的许可。
