import AVFoundation
import Foundation

@MainActor
final class FaceTrackViewModel: ObservableObject {
    @Published var config: AppConfig {
        didSet {
            ConfigStore.save(config)
        }
    }
    @Published var status: TrackingStatus = .idle
    @Published var isStreaming = false
    @Published var isSettingsOpen = false
    @Published var isCalibrationOpen = false
    @Published var fps: Int = 0
    @Published var lastRawFaceData: [String: Float] = [:]
    @Published var lastStreamFaceData: [String: Float] = [:]
    @Published var lastBlendshapes: [String: Float] = [:]
    @Published var backendLabel = "CPU"
    @Published var paramMapStatus = ""

    let cameraService = CameraService()

    private let landmarker = FaceLandmarkerService()
    private let streamer = FaceStreamer()
    private var frameCount = 0
    private var fpsStart = Date()
    private var modelLoaded = false
    private var paramMapFloatCount = 0
    private var paramMapBinaryCount = 0

    init() {
        config = ConfigStore.load()
        paramMapStatus = L.text(.defaultOscAddresses, config.language)
        bindServices()
        loadModel()
    }

    func toggleLanguage() {
        config.language = config.language.toggled
        refreshLocalizedState()
    }

    func loadModel() {
        status = .loadingModel
        do {
            try landmarker.load(config: config)
            modelLoaded = true
            status = .modelReady
        } catch {
            modelLoaded = false
            status = .error(error.localizedDescription)
        }
    }

    func toggleCamera() {
        Task {
            if cameraService.isRunning {
                stopCamera()
            } else {
                await startCamera()
            }
        }
    }

    func startCamera() async {
        guard await cameraService.requestPermissionIfNeeded() else {
            status = .error("Camera permission denied")
            return
        }
        guard modelLoaded else {
            loadModel()
            guard modelLoaded else { return }
            return
        }
        cameraService.start()
        status = .waitingForFace
    }

    func stopCamera() {
        if isStreaming {
            stopStreaming()
        }
        cameraService.stop()
        status = .cameraStopped
    }

    func switchCamera() {
        cameraService.switchCamera()
    }

    func toggleStreaming() {
        isStreaming ? stopStreaming() : startStreaming()
    }

    func startStreaming() {
        guard cameraService.isRunning else {
            status = .error("Start camera before streaming")
            return
        }
        guard streamer.connect(host: config.host, oscPort: config.oscPort, vmcPort: config.vmcPort) else {
            status = .error("Streaming connection failed")
            return
        }
        isStreaming = true
        status = .streaming
        Task {
            let (floatMap, binaryMap) = await ParamMapLoader.fetch(host: config.host)
            streamer.updateParamMap(floatMap: floatMap, binaryMap: binaryMap)
            await MainActor.run {
                paramMapFloatCount = floatMap.count
                paramMapBinaryCount = binaryMap.count
                if floatMap.isEmpty && binaryMap.isEmpty {
                    paramMapStatus = L.text(.defaultOscAddresses, config.language)
                } else {
                    paramMapStatus = L.format(.loadedParamMap, config.language, floatMap.count, binaryMap.count)
                }
            }
        }
    }

    func stopStreaming() {
        streamer.disconnect()
        isStreaming = false
        status = cameraService.isRunning ? .waitingForFace : .cameraStopped
    }

    func calibratePupil() {
        guard !lastBlendshapes.isEmpty else {
            status = .error("No face data for pupil calibration")
            return
        }
        config.eyeCalibration = EyeCalibrationOffset(
            lookOutLeft: lastBlendshapes["eyeLookOutLeft", default: 0],
            lookInLeft: lastBlendshapes["eyeLookInLeft", default: 0],
            lookInRight: lastBlendshapes["eyeLookInRight", default: 0],
            lookOutRight: lastBlendshapes["eyeLookOutRight", default: 0],
            lookUpLeft: lastBlendshapes["eyeLookUpLeft", default: 0],
            lookDownLeft: lastBlendshapes["eyeLookDownLeft", default: 0],
            lookUpRight: lastBlendshapes["eyeLookUpRight", default: 0],
            lookDownRight: lastBlendshapes["eyeLookDownRight", default: 0]
        )
    }

    func resetPupilCalibration() {
        config.eyeCalibration = EyeCalibrationOffset()
    }

    func setEyeRangeMin() {
        guard !lastRawFaceData.isEmpty else {
            status = .error("No face data for eye range minimum")
            return
        }
        config.eyeRangeCalibration.minValues = FaceMapping.captureEyeRangeValues(lastRawFaceData)
    }

    func setEyeRangeMax() {
        guard !lastRawFaceData.isEmpty else {
            status = .error("No face data for eye range maximum")
            return
        }
        config.eyeRangeCalibration.maxValues = FaceMapping.captureEyeRangeValues(lastRawFaceData)
    }

    func resetEyeRange() {
        config.eyeRangeCalibration = EyeRangeCalibration()
    }

    func calibrateMouthClosed() {
        guard !lastBlendshapes.isEmpty else {
            status = .error("No face data for mouth calibration")
            return
        }
        config.mouthCalibration.closedJawOpen = lastBlendshapes["jawOpen", default: 0]
        config.mouthCalibration.closedMouthClose = lastBlendshapes["mouthClose", default: 0]
    }

    func calibrateMouthMaxOpen() {
        guard !lastBlendshapes.isEmpty else {
            status = .error("No face data for mouth maximum")
            return
        }
        config.mouthCalibration.maxJawOpen = lastBlendshapes["jawOpen", default: 0]
        config.mouthCalibration.maxMouthClose = lastBlendshapes["mouthClose", default: 0]
    }

    func resetMouthCalibration() {
        config.mouthCalibration = MouthCalibrationOffset()
    }

    private func bindServices() {
        cameraService.onFrame = { [weak self] sampleBuffer, _ in
            Task { @MainActor in
                guard let self = self else { return }
                self.landmarker.detect(sampleBuffer: sampleBuffer, mirrored: self.config.isMirrored)
            }
        }

        landmarker.onFrame = { [weak self] blendshapes in
            Task { @MainActor in
                self?.handleBlendshapes(blendshapes)
            }
        }

        landmarker.onFaceMissing = { [weak self] in
            Task { @MainActor in
                guard let self = self else { return }
                self.status = self.isStreaming ? .streaming : .waitingForFace
            }
        }

        landmarker.onError = { [weak self] error in
            Task { @MainActor in
                self?.status = .error(error.localizedDescription)
            }
        }
    }

    private func handleBlendshapes(_ blendshapes: [String: Float]) {
        let rawFaceData = FaceMapping.extractFaceData(
            blendshapes: blendshapes,
            eyeSensitivity: config.eyeSensitivity,
            eyeCalibration: config.eyeCalibration,
            mouthSensitivity: config.mouthSensitivity,
            mouthCalibration: config.mouthCalibration,
            isMirrored: false,
            invertEyeX: config.invertEyeX,
            invertEyeY: config.invertEyeY,
            syncEyes: config.syncEyes,
            sendMergedEyes: config.sendMergedEyes
        )
        let streamFaceData = FaceMapping.applyEyeRangeCalibration(
            rawFaceData,
            calibration: config.eyeRangeCalibration
        )

        lastBlendshapes = blendshapes
        lastRawFaceData = rawFaceData
        lastStreamFaceData = streamFaceData
        tickFps()

        if isStreaming {
            streamer.sendFaceData(streamFaceData)
            status = .streaming
        } else {
            status = .faceDetected
        }
    }

    private func tickFps() {
        frameCount += 1
        let elapsed = Date().timeIntervalSince(fpsStart)
        guard elapsed >= 1 else { return }
        fps = Int(Double(frameCount) / elapsed)
        frameCount = 0
        fpsStart = Date()
    }

    private func refreshLocalizedState() {
        if paramMapFloatCount > 0 || paramMapBinaryCount > 0 {
            paramMapStatus = L.format(.loadedParamMap, config.language, paramMapFloatCount, paramMapBinaryCount)
        } else {
            paramMapStatus = L.text(.defaultOscAddresses, config.language)
        }
    }
}
