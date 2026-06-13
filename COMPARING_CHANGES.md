# Comparing Changes / 变更对比

Branch comparison: `main` -> `vr-headset-validation`

分支对比：`main` -> `vr-headset-validation`

## Summary / 摘要

This branch adds a Quest/Pico VR headset validation build on top of the main FaceTrack Android app. It focuses on validating multi-camera external capture on headsets while keeping the original FaceTrack VRCFT/VMC/OSC face streaming path intact.

该分支在主仓库 FaceTrack Android 应用基础上增加 Quest/Pico VR 头显验证版本，重点用于验证头显上的多路外接摄像头采集链路，同时保留原有 FaceTrack VRCFT/VMC/OSC 面捕推流能力。

## Main Repository / 主仓库定位

- Uses the Android system front/rear camera for MediaPipe face tracking.
- Streams face tracking data to VR applications through VMC/OSC.
- Targets normal Android phones/tablets or Android devices with a single primary camera.
- Focuses on the full face tracking and avatar-driving workflow.

- 使用 Android 系统前/后摄像头进行 MediaPipe 面部追踪。
- 通过 VMC/OSC 将面捕数据推流到 VR 应用。
- 主要面向普通 Android 手机、平板或具备单路主摄的 Android 设备。
- 重点是完整面捕和驱动虚拟形象流程。

## VR Headset Branch / VR 头显分支定位

- Runs as a 2D Android utility on Quest/Pico headsets.
- Validates whether the headset can enumerate and capture up to three external cameras.
- Assigns external camera feeds to left eye, right eye, and mouth roles.
- Provides live preview and device health information for each role.
- Keeps the existing face tracking and streaming path separate from the external camera validation path.

- 作为 Quest/Pico 头显上的 2D Android 工具运行。
- 验证头显是否能枚举并采集最多三路外接摄像头。
- 将外接摄像头画面分别绑定到左眼、右眼、嘴部角色。
- 为每个角色显示实时预览和设备状态信息。
- 将现有面捕推流链路与外接摄像头验证链路保持隔离。

## Feature Highlights / 特色功能

### Three External Camera Validation / 三路外接摄像头验证

The branch adds three camera validation slots for left eye, right eye, and mouth. Each slot can show preview, connection state, permission state, FPS, resolution, and dropped-frame count.

该分支新增左眼、右眼、嘴部三个摄像头验证槽位。每个槽位可显示预览画面、连接状态、权限状态、FPS、分辨率和丢帧计数。

### Manual Camera Binding / 手动摄像头绑定

Each role has a binding dropdown. Users can keep automatic assignment or manually bind a specific detected Camera2 external/UVC device to left eye, right eye, or mouth.

每个部位都有独立绑定下拉框。用户可以保持自动分配，也可以手动将检测到的 Camera2 external 或 UVC 摄像头绑定到左眼、右眼或嘴部。

### Camera2 External First / 优先使用 Camera2 外部摄像头

If the headset exposes USB cameras through Android Camera2 as `LENS_FACING_EXTERNAL`, this branch opens them through the Camera2 validation controller.

如果头显通过 Android Camera2 将 USB 摄像头暴露为 `LENS_FACING_EXTERNAL`，该分支会优先通过 Camera2 验证控制器打开这些摄像头。

### USB UVC Fallback / USB UVC 兜底

If Camera2 does not expose the external cameras, the branch falls back to USB Host plus `com.herohan:UVCAndroid:1.0.12` for direct UVC enumeration and preview.

如果 Camera2 不暴露外接摄像头，该分支会回退到 USB Host + `com.herohan:UVCAndroid:1.0.12`，直接枚举并预览 UVC 设备。

### Foreground Service Capture / 前台服务采集

External camera validation is held by a foreground service with wake-lock support. This allows validation of whether capture can continue while the app is backgrounded or the headset screen is off.

外接摄像头验证由带 WakeLock 的前台服务持有，可用于验证 App 切后台或头显息屏后采集链路是否仍能保持。

### Included APK / 附带 APK

The branch includes the compiled validation APK:

该分支包含已编译的验证 APK：

`release/FaceTrack-Standalone-v1.4-quest-pico-uvc-validation-debug.apk`

## Changed Files / 主要变更文件

- `.gitignore`
- `VR_HEADSET_VALIDATION.md`
- `COMPARING_CHANGES.md`
- `android/app/build.gradle`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/facetrack/standalone/Camera2ExternalCameraController.kt`
- `android/app/src/main/java/com/facetrack/standalone/CameraInputSource.kt`
- `android/app/src/main/java/com/facetrack/standalone/FaceTrackingService.kt`
- `android/app/src/main/java/com/facetrack/standalone/MultiUvcCameraController.kt`
- `android/app/src/main/java/com/facetrack/standalone/MainActivity.kt`
- `android/app/src/main/res/layout/activity_main.xml`
- `android/app/src/main/res/xml/device_filter.xml`
- `release/FaceTrack-Standalone-v1.4-quest-pico-uvc-validation-debug.apk`

## Current Scope / 当前范围

This branch is a hardware capture-chain validation branch. It does not yet perform separate local eye/mouth inference on the three external camera feeds, and it does not stream local three-camera recognition results to VRCFT.

该分支是硬件采集链路验证分支。目前尚未对三路外接摄像头画面进行独立眼睛/嘴部推理，也不会把三摄局部识别结果推流到 VRCFT。

After Quest/Pico capture stability is confirmed, the next step can connect the three camera feeds to dedicated eye/mouth models and then map those results into VRCFT parameters.

在确认 Quest/Pico 三路采集稳定后，下一阶段可以将三路摄像头接入专用眼部/嘴部模型，并将结果映射到 VRCFT 参数。

## Validation Checklist / 验证清单

1. Sideload the APK to Quest/Pico.
2. Grant camera, notification, and USB permissions.
3. Connect a powered USB hub and up to three UVC cameras.
4. Start external camera validation.
5. Bind cameras to left eye, right eye, and mouth.
6. Confirm preview, FPS, resolution, and dropped-frame counters for each role.
7. Put the headset to sleep or background the app and verify the foreground service remains active.
8. Return to the app and confirm preview recovers.

1. 将 APK 侧载到 Quest/Pico。
2. 授予摄像头、通知和 USB 权限。
3. 连接供电 USB Hub 和最多三路 UVC 摄像头。
4. 启动外接摄像头验证。
5. 将摄像头绑定到左眼、右眼、嘴部。
6. 确认每个部位都有预览、FPS、分辨率和丢帧计数。
7. 让头显息屏或将 App 切后台，确认前台服务仍保持运行。
8. 返回 App，确认预览可以恢复。
