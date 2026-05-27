# FaceTrack-Standalone | 面部追踪独立版

[![License](https://img.shields.io/badge/License-MIT%20Non--Commercial-red.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20ARM64-green.svg)]()

Real-time facial landmark tracking and VMC/OSC streaming for Android, powered by MediaPipe.

基于 MediaPipe 的 Android 实时面部关键点追踪与 VMC/OSC 推流工具。

---

## Features | 功能特性

- **Real-time Face Tracking** - 52 blendshape detection via MediaPipe FaceLandmarker
  **实时面部追踪** - 基于 MediaPipe FaceLandmarker 的 52 个混合形状检测

- **VMC/OSC Streaming** - UDP streaming to VR applications (VMC protocol on port 39539, OSC on port 9000)
  **VMC/OSC 推流** - UDP 推流至 VR 应用（VMC 协议端口 39539，OSC 端口 9000）

- **GPU Acceleration** - Automatic GPU/NPU detection for Snapdragon 8 Elite, Dimensity 9400+, Kirin 9010
  **GPU 加速** - 自动检测骁龙 8 Elite、天玑 9400+、麒麟 9010 的 GPU/NPU 加速

- **Camera Management** - Front/rear camera detection and switching
  **摄像头管理** - 前后摄像头检测与切换

- **Configurable UI** - Full-screen camera preview with collapsible settings panel
  **可配置界面** - 全屏摄像头预览 + 可折叠设置面板

---

## Screenshots | 截图

> Camera preview with floating controls and settings panel
> 摄像头全屏预览，浮动控制按钮与设置面板

---

## Download | 下载

Get the latest APK from [Releases](../../releases).

从 [Releases](../../releases) 获取最新 APK 安装包。

### Requirements | 系统要求

- Android 8.0+ (API 26+)
- ARM64 device | ARM64 设备
- Front-facing camera | 前置摄像头

---

## Usage | 使用方法

1. Install APK | 安装 APK
2. Grant camera permission | 授予摄像头权限
3. Tap camera button (bottom-left) to start preview | 点击左下角摄像头按钮开启预览
4. Tap settings button (bottom-right) to configure streaming | 点击右下角设置按钮配置推流
5. Enter OSC address, OSC port, VMC port | 输入 OSC 地址、OSC 端口、VMC 端口
6. Tap "Start Streaming" | 点击「启动推流」

### Camera Controls | 摄像头控制

| Button | Function |
|--------|----------|
| Camera (bottom-left) | Toggle camera on/off |
| Switch (next to camera) | Switch front/rear camera |
| Settings (bottom-right) | Open/close settings panel |

| 按钮 | 功能 |
|------|------|
| 摄像头（左下角） | 开启/关闭摄像头 |
| 切换（摄像头旁） | 切换前置/后置摄像头 |
| 设置（右下角） | 打开/关闭设置面板 |

### Default Ports | 默认端口

| Protocol | Port | Description |
|----------|------|-------------|
| OSC | 9000 | Face blendshape data |
| VMC | 39539 | VMC protocol streaming |

| 协议 | 端口 | 说明 |
|------|------|------|
| OSC | 9000 | 面部混合形状数据 |
| VMC | 39539 | VMC 协议推流 |

---

## Build | 构建

### Prerequisites | 前置条件

- Android Studio Hedgehog+
- Android SDK 34
- NDK 26.1.10909125
- CMake 3.22.1+

### Steps | 步骤

```bash
cd android
./gradlew assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

---

## Tech Stack | 技术栈

- **MediaPipe Tasks Vision** - Face landmark detection
- **CameraX** - Camera capture and preview
- **VMC Protocol** - Virtual Motion Capture streaming
- **OSC Protocol** - Open Sound Control for blendshapes
- **Kotlin** - Android application logic
- **C++ / NDK** - Hardware acceleration detection

---

## Project Structure | 项目结构

```
FaceTrack-Standalone/
├── android/                    # Android project
│   ├── app/src/main/
│   │   ├── java/.../           # Kotlin source
│   │   │   ├── MainActivity.kt         # Main UI & camera
│   │   │   ├── FaceVMCStreamer.kt      # VMC/OSC streaming
│   │   │   ├── MediaPipeHelper.kt      # Face data extraction
│   │   │   ├── ModelHelper.kt          # Model file management
│   │   │   ├── NativeHelper.kt         # Hardware detection
│   │   │   └── Helpers.kt              # AppConfig & utilities
│   │   ├── cpp/                # Native C++ code
│   │   │   ├── native-lib.cpp           # NDK acceleration
│   │   │   └── CMakeLists.txt           # Build config
│   │   ├── assets/             # ML model
│   │   └── res/layout/         # UI layouts
│   └── build.gradle
├── streamers/                  # Python VMC/OSC streamers
├── main.py                     # Python entry point
├── LICENSE
└── README.md
```

---

## License | 许可证

MIT License with Non-Commercial Restriction - see [LICENSE](LICENSE)

This software is free for personal and non-commercial use. Commercial use requires explicit written permission from the author.

MIT 许可证（禁止商用） - 详见 [LICENSE](LICENSE)

本软件免费供个人和非商业用途使用。商用需获得作者书面授权。

---

## Acknowledgments | 致谢

- [MediaPipe](https://mediapipe.dev/) - Face landmark detection framework
- [VMC Protocol](https://sh-akira.github.io/VirtualMotionCaptureProtocol/) - Virtual Motion Capture protocol specification
