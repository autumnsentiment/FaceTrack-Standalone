# VR Headset Validation Branch

This branch packages the current Android build as a Quest/Pico external-camera validation version of FaceTrack-Standalone.

## Purpose

- Run the APK as a 2D Android utility on Quest/Pico headsets.
- Validate whether the headset can enumerate and keep capturing up to three external cameras.
- Assign the three camera feeds to left eye, right eye, and mouth roles.
- Keep the existing FaceTrack VRCFT/VMC/OSC face-streaming path intact.

## Included APK

- `release/FaceTrack-Standalone-v1.4-quest-pico-uvc-validation-debug.apk`
- Version name: `1.4`
- Build type: debug
- Target architecture: `arm64-v8a`

## Key Behavior

- Camera2 `LENS_FACING_EXTERNAL` is preferred when the headset exposes USB cameras through the Android camera stack.
- If Camera2 external cameras are not exposed, the app falls back to USB Host plus `com.herohan:UVCAndroid:1.0.12`.
- The settings panel contains an external camera validation area with:
  - Three live preview slots.
  - Left eye, right eye, and mouth camera binding dropdowns.
  - Per-role device state, FPS, resolution, and dropped-frame counters.
- A foreground service keeps validation capture alive while the app is backgrounded or the screen is off.
- This branch only validates capture, preview, device status, and role binding. It does not run local eye/mouth inference on the three external camera feeds and does not stream local UVC recognition results to VRCFT.

## Device Validation Checklist

1. Sideload the APK to a Quest/Pico headset.
2. Grant camera, notification, and USB device permissions.
3. Connect a powered USB hub and up to three UVC cameras.
4. Open the settings panel and start external camera validation.
5. Bind the detected cameras to left eye, right eye, and mouth.
6. Confirm each role shows preview, FPS, resolution, and stable connection state.
7. Put the headset to sleep or send the app to background for several minutes and confirm the foreground notification remains active.
8. Return to the app and confirm preview resumes.

## Current Scope

This is a hardware-link validation branch. The next phase can connect the three local camera feeds to specialized eye/mouth inference and VRCFT parameter streaming after Quest/Pico capture stability is confirmed.
