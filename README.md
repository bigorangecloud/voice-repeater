# Silero 变声复读机

一个 Android App：你说一句话，应用通过 **Silero VAD** 检测到你说完后，自动把这句话**变声复读**出来。支持原声 / 男声 / 女声 / 婴儿声预设，音高、语速、断句灵敏度均可调。

## 工作原理

```
麦克风 → AudioRecord(16kHz/单声道/PCM16)
       → Silero VAD (ONNX, 每 512 采样=32ms 输出语音概率)
       → 端点检测 (进入阈值/退出阈值/尾部静音判定"说完")
       → 缓存整句 PCM
       → SoundTouch 变声 (音高+语速移位, JNI/C++)
       → AudioTrack 回放
```

回放期间会暂停录音，避免把自己的声音再录进去。

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

## 构建

依赖本机：Android SDK（含 NDK 26.x、CMake 3.22.1、platform-34）。

```powershell
# 直接命令行构建（JAVA_HOME 指向 JDK 17+）
.\gradlew.bat assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk
```

或用 Android Studio 直接打开本目录，等待 Gradle sync 后点 Run。

## 资源来源

- Silero VAD 模型：https://github.com/snakers4/silero-vad （`app/src/main/assets/silero_vad.onnx`）
- SoundTouch：https://codeberg.org/soundtouch/soundtouch （LGPL）
