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

- **AUTO Backend** - Automatic inference backend selection (QNN > NNAPI > GPU > CPU) based on device capability
  **自动后端** - 根据设备能力自动选择推理后端（QNN > NNAPI > GPU > CPU）

- **Universal ARM64** - armv8-a baseline, compatible with all ARM64 devices including low-end SoCs
  **通用 ARM64** - armv8-a 基线，兼容所有 ARM64 设备（含低端 SOC）

- **Cross-Vendor GPU Detection** - EGL-based GPU renderer detection for Adreno/Mali/Immortalis/Maleoon
  **跨厂商 GPU 检测** - 基于 EGL 的 GPU 渲染器检测，支持 Adreno/Mali/Immortalis/Maleoon

- **Camera Management** - Front/rear camera detection and switching, camera off by default
  **摄像头管理** - 前后摄像头检测与切换，默认关闭

- **Configurable UI** - Full-screen camera preview with collapsible settings panel and inference hardware selector
  **可配置界面** - 全屏摄像头预览 + 可折叠设置面板 + 推理硬件选择器

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

### Supported SoCs | 支持 SOC

| SoC | GPU | NPU | AUTO Backend | Notes |
|-----|-----|-----|-------------|-------|
| Snapdragon 8 Elite | Adreno 830 | Hexagon | QNN→GPU | Best performance |
| Snapdragon 8 Gen 3 | Adreno 750 | Hexagon | QNN→GPU | |
| Snapdragon 8 Gen 2 | Adreno 740 | Hexagon | QNN→GPU | |
| Dimensity 9400+ | Immortalis-G925 | APU 790 | NNAPI→GPU | |
| Dimensity 9300 | Immortalis-G720 | APU | NNAPI→GPU | |
| Dimensity 8300 | Mali-G615 | APU | NNAPI→GPU | |
| Kirin 9010 | Maleoon 910 | Da Vinci | NNAPI→GPU | |
| Kirin 9000S | Maleoon 750 | Da Vinci | NNAPI→GPU | |
| Unisoc T820 | Mali-G610 | Yes | NNAPI→GPU | |
| Unisoc T7520/T770 | Mali-G57 | Yes | NNAPI→GPU | |
| Unisoc T610/T618 | Mali-G52 | No | **CPU** | Mali-G52 driver issues |
| Other ARM64 | Any | - | GPU/CPU | Auto fallback |

> **Note**: MediaPipe Tasks Vision only supports GPU and CPU delegates. NPU/QNN options fall back to GPU with notification.
> **注意**: MediaPipe Tasks Vision 仅支持 GPU 和 CPU 后端。NPU/QNN 选项会降级到 GPU 并提示。

---

## Changelog | 更新日志

### v1.2.0 - Universal ARM64 Support

| Category | v1.0 | v1.2 |
|----------|------|------|
| CPU Baseline | armv8.2-a+fp16+dotprod | **armv8-a** (all ARM64) |
| GPU Detection | Adreno-only (sysfs) | **EGL universal** (Adreno/Mali/Immortalis/Maleoon) |
| Backend Options | GPU / CPU | **AUTO / GPU / NPU / QNN / CPU** |
| AUTO Logic | None | **QNN > NNAPI > GPU > CPU** |
| Unisoc Support | ❌ Crash (SIGILL) | ✅ T610/T618/T7520/T820/UD710 |
| Camera Default | Auto-start | **Off by default** |
| Settings Panel | No hardware selector | **Inference hardware selector** |
| OSC/VMC Ports | Hardcoded | **Configurable** |
| Camera Switch | No | **Front/rear toggle** |
| Mali GPU | Not detected | **Detected + NNAPI preferred** |
| T610/T618 | ❌ SIGILL crash | ✅ CPU fallback |

**Key Changes:**
- CMake: `armv8-a` baseline replaces `armv8.2-a+fp16+dotprod`, eliminating SIGILL crashes on low-end SoCs
- NativeHelper: AUTO backend with Unisoc T610/T618 CPU fallback (Mali-G52 driver issues)
- native-lib: EGL-based GPU renderer detection replacing Qualcomm-specific sysfs paths
- UI: Secondary menu with inference hardware selector (AUTO/GPU/NPU/QNN/CPU)
- Camera: Default off, manual start with front/rear switching

### v1.0.0 - Initial Release

- MediaPipe FaceLandmarker with 52 blendshapes
- VMC/OSC dual-protocol streaming
- CameraX front camera
- GPU acceleration (Snapdragon only)

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
