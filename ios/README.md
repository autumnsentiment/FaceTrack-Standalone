# FaceTrack-Standalone iOS

Native SwiftUI iOS port for FaceTrack-Standalone v1.4.

## Build

1. Install CocoaPods on macOS.
2. Run:

```sh
cd ios
pod install
open FaceTrackStandalone.xcworkspace
```

Select a development team in Xcode before running on a physical iPhone. The app uses the camera and local network for OSC/VMC UDP streaming.

The repository includes `FaceTrackStandalone/Resources/face_landmarker.task`. If you replace it, keep the same filename so `FaceLandmarkerService` can load it from the app bundle.

## iOS API Notes

- Camera capture uses AVFoundation.
- Face tracking uses `MediaPipeTasksVision` in live-stream mode.
- OSC and VMC UDP packets use Network.framework.
- iOS MediaPipe Tasks are configured with CPU execution. Android GPU/NPU/QNN choices are shown as a platform note instead of unsupported controls.

## Validation

This project was created from a Windows workspace, so Xcode build validation must be run on macOS:

```sh
cd ios
pod install
xcodebuild -workspace FaceTrackStandalone.xcworkspace -scheme FaceTrackStandalone -destination 'generic/platform=iOS' build
```

## IPA Packaging

An iOS install package (`.ipa`) must be signed and exported on macOS with Xcode and an Apple Developer account.

```sh
cd ios
chmod +x scripts/build-ipa.sh
./scripts/build-ipa.sh
```

The default export method is `development`, configured in `ExportOptions.plist`. Change `method` when needed:

- `development`: install on devices registered to your developer account.
- `ad-hoc`: distribute to registered UDIDs.
- `app-store`: upload through App Store Connect or TestFlight.

The exported IPA will be written to `ios/build/export/`.

For cloud macOS builds, see [`CI_PACKAGING.md`](CI_PACKAGING.md). The repository includes ready-to-edit configs for GitHub Actions, Codemagic, and Bitrise.
