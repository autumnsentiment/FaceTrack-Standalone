# FaceTrack-Standalone Android 项目说明

## 概述
基于 MediaPipe CameraX 和 MediaPipe Tasks Vision 的独立面部追踪安卓应用。

## 支持平台
- **骁龙 (Snapdragon)**: 支持 GPU (Adreno) + NPU (Hexagon) 加速
- **联发科 (MediaTek)**: 支持 GPU (Mali) + APU 加速
- **麒麟 (Kirin)**: 支持 GPU (Mali) + NPU (Da Vinci) 加速

## 架构设计

```
┌─────────────────────────────────────────────────────┐
│                   MainActivity                       │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ CameraX     │  │ FaceLand-    │  │ OSC/VMC   │  │
│  │ 摄像头预览   │→│ marker 推理   │→│ UDP 推流   │  │
│  └─────────────┘  └──────────────┘  └───────────┘  │
│       ↑                  ↓                         │
│  ┌─────────────┐  ┌──────────────┐                 │
│  │ UI 控制层    │  │ 状态显示层    │                 │
│  └─────────────┘  └──────────────┘                 │
└─────────────────────────────────────────────────────┘
```

## 核心模块

### 1. 摄像头 (CameraX)
- 使用 CameraX Preview 实现实时预览
- ImageAnalysis 分析器输出帧给 MediaPipe
- 支持前后摄像头切换

### 2. 面部推理 (MediaPipe Tasks Vision)
- 使用 `face_landmarker.task` 模型
- LIVE_STREAM 异步推理模式
- 支持 GPU / NNAPI / CPU 后端自动选择

### 3. 推流 (UDP Socket)
- OSC 推流 (端口 9000)
- VMC 推流 (端口 39539)
- 异步线程发送，不阻塞推理

## 构建部署

### 方式 1: Android Studio (推荐)
```bash
# 打开 android 目录
android studio android/

# 连接安卓设备，点击 Run
```

### 方式 2: 命令行构建
```bash
cd android
./gradlew assembleDebug
# APK 位置: app/build/outputs/apk/debug/app-debug.apk
```

### 方式 3: 直接安装
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 依赖配置

### MediaPipe Tasks Vision
```gradle
implementation 'com.google.mediapipe:tasks-vision:0.10.16'
```
- 内置 TFLite 运行时
- 自动选择最佳加速后端

### CameraX
```gradle
implementation "androidx.camera:camera-camera2:1.3.0"
```
- 统一摄像头 API，兼容所有安卓设备

## 模型文件

将 `face_landmarker.task` 放入 `app/src/main/assets/` 目录:
```bash
cp ../face_landmarker.task app/src/main/assets/
```

## 配置

在 `MainActivity.kt` 中修改:
```kotlin
val config = Config(
    host = "127.0.0.1",       // 推流目标 IP
    oscPort = 9000,            // OSC 端口
    vmcPort = 39539,           // VMC 端口
    cameraId = CameraSource.FRONT  // 摄像头
)
```

## 注意事项

1. **网络**: 确保安卓设备与推流目标在同一局域网
2. **性能**: 建议在高性能模式下运行 (骁龙 8+/联发科 9000+/麒麟 9000+)
3. **发热**: 持续推理会发热，建议连接充电器
4. **权限**: 首次启动需授予摄像头权限
