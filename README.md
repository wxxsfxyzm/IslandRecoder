# Island Recorder

<div align="center">

### 专业的 Android 屏幕录制应用

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/Leaf-lsgtky/IslandRecoder/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-brightgreen.svg)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-24-orange.svg)](https://developer.android.com/about/versions/nougat)

</div>

---

## 📱 关于

**Island Recorder** 是一款功能强大的 Android 屏幕录制应用，基于 [Flux Recorder](https://github.com/Icradle-Innovations-Ltd/FluxRecorder) 二次开发。在保留原有优秀功能的基础上，添加了动态 HDR 录制支持、MIUI/HyperOS 优化等特性。

---

## ✨ 主要特性

### 录制功能
- **多分辨率选项** - 1080p, 720p, 480p, 360p 及原生分辨率
- **可变帧率** - 15/24/30/48/60/90/120 FPS 可选
- **动态 HDR 支持** - H.265 编码时自动检测并切换 HDR 内容（Android 10+）
- **多种编码格式** - H.264 (AVC) / H.265 (HEVC)
- **灵活音频录制** - 系统内录 / 麦克风 / 双音源 / 静音

### 界面与体验
- **MIUI/HyperOS 优化** - 完美支持边到边显示
- **Material Design 3** - 简洁现代的界面
- **悬浮球控制** - 录制过程中便捷操作
- **无水印** - 完全免费

### 高级功能
- **自动文件管理** - 智能存储空间管理
- **方向感知录制** - 自动适配横竖屏
- **触摸可视化** - 录制时显示触摸点（需要 Root）

### 隐私保护
- **不收集任何数据**
- **无分析或追踪**
- **所有录制本地存储**
- **用户完全控制**

---

## 🚀 下载

从 [Releases](https://github.com/Leaf-lsgtky/IslandRecoder/releases) 页面下载最新 APK

---

## 🛠️ 技术细节

- **构建语言:** Kotlin, Jetpack Compose
- **架构:** MVVM with Clean Architecture
- **依赖注入:** Hilt
- **媒体框架:** MediaCodec, MediaMuxer, MediaProjection
- **最低 SDK:** 24 (Android 7.0)
- **目标 SDK:** 35 (Android 15)

---

## 📋 权限说明

| 权限 | 用途 |
|------|------|
| RECORD_AUDIO | 录制系统音频和/或麦克风输入 |
| FOREGROUND_SERVICE | 后台维持录制 |
| POST_NOTIFICATIONS | 显示录制状态 |
| SYSTEM_ALERT_WINDOW | 悬浮控制覆盖层 |

---

## 🔒 隐私政策

我们认真对待您的隐私。Island Recorder：
- 不收集任何个人数据
- 不使用分析或追踪
- 不与第三方共享数据
- 所有录制存储在您的设备本地

---

## 📝 开源协议

本项目基于 **MIT License** - 详见 [LICENSE](LICENSE) 文件。

本项目基于原 Flux Recorder 项目修改，原项目版权归 Icradle Innovations Ltd 所有。

---

## 🤝 贡献

欢迎贡献！请随时提交 Pull Request。

---

## 📧 联系方式

- **Issues:** [GitHub Issues](https://github.com/Leaf-lsgtky/IslandRecoder/issues)

---

## 🙏 致谢

- 原项目：[Flux Recorder](https://github.com/Icradle-Innovations-Ltd/FluxRecorder) by Icradle Innovations Ltd
- UI 框架：[miuix-compose](https://github.com/yukonga/miuix-compose)

---

<div align="center">

使用 Kotlin 和 Jetpack Compose 构建 ❤️

</div>

---

---

# Island Recorder

<div align="center">

### Professional Screen Recording for Android

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/Leaf-lsgtky/IslandRecoder/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-brightgreen.svg)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-24-orange.svg)](https://developer.android.com/about/versions/nougat)

</div>

---

## 📱 About

**Island Recorder** is a powerful screen recording application for Android, forked from [Flux Recorder](https://github.com/Icradle-Innovations-Ltd/FluxRecorder). It retains all the original features while adding dynamic HDR recording support, MIUI/HyperOS optimizations, and more.

---

## ✨ Features

### Recording
- **Multiple resolution options** - 1080p, 720p, 480p, 360p and native
- **Variable frame rate** - 15/24/30/48/60/90/120 FPS
- **Dynamic HDR support** - Auto-detect and switch HDR content during H.265 recording (Android 10+)
- **Multiple codecs** - H.264 (AVC) / H.265 (HEVC)
- **Flexible audio capture** - Internal / Microphone / Both / Silent

### UI & UX
- **MIUI/HyperOS Optimized** - Perfect edge-to-edge display support
- **Material Design 3** - Clean and modern interface
- **Floating controls** - Easy access during recording
- **No watermarks** - Completely free

### Advanced Features
- **Automatic file management** - Smart storage management
- **Orientation-aware recording** - Auto-adapt to portrait/landscape
- **Touch visualization** - Show touch points during recording (Root required)

### Privacy
- **No data collection**
- **No analytics or tracking**
- **All recordings stored locally**
- **Full user control**

---

## 🚀 Download

Download the latest APK from the [Releases](https://github.com/Leaf-lsgtky/IslandRecoder/releases) page

---

## 🛠️ Technical Details

- **Built with:** Kotlin, Jetpack Compose
- **Architecture:** MVVM with Clean Architecture
- **Dependency Injection:** Hilt
- **Media:** MediaCodec, MediaMuxer, MediaProjection
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35 (Android 15)

---

## 📋 Permissions

| Permission | Purpose |
|------------|---------|
| RECORD_AUDIO | Capture system audio and/or microphone input |
| FOREGROUND_SERVICE | Maintain recording in background |
| POST_NOTIFICATIONS | Display recording status |
| SYSTEM_ALERT_WINDOW | Floating control overlay |

---

## 🔒 Privacy

We take your privacy seriously. Island Recorder:
- Does not collect any personal data
- Does not use analytics or tracking
- Does not share data with third parties
- Stores all recordings locally on your device

---

## 📝 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

This project is a fork of the original Flux Recorder, copyright owned by Icradle Innovations Ltd.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

## 📧 Contact

- **Issues:** [GitHub Issues](https://github.com/Leaf-lsgtky/IslandRecoder/issues)

---

## 🙏 Acknowledgments

- Original Project: [Flux Recorder](https://github.com/Icradle-Innovations-Ltd/FluxRecorder) by Icradle Innovations Ltd
- UI Framework: [miuix-compose](https://github.com/yukonga/miuix-compose)

---

<div align="center">

Made with ❤️ using Kotlin and Jetpack Compose

</div>
