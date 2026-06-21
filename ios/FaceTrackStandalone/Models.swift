import Foundation

struct AppConfig: Codable, Equatable {
    var host = "127.0.0.1"
    var oscPort = 9000
    var vmcPort = 39539
    var faceDetectionConfidence: Float = 0.5
    var facePresenceConfidence: Float = 0.5
    var faceTrackingConfidence: Float = 0.5
    var eyeSensitivity: Float = 1.0
    var eyeCalibration = EyeCalibrationOffset()
    var eyeRangeCalibration = EyeRangeCalibration()
    var mouthSensitivity: Float = 1.0
    var mouthCalibration = MouthCalibrationOffset()
    var isMirrored = false
    var invertEyeX = false
    var invertEyeY = false
    var syncEyes = true
    var sendMergedEyes = false
    var language: AppLanguage = .english

    init() {}

    enum CodingKeys: String, CodingKey {
        case host
        case oscPort
        case vmcPort
        case faceDetectionConfidence
        case facePresenceConfidence
        case faceTrackingConfidence
        case eyeSensitivity
        case eyeCalibration
        case eyeRangeCalibration
        case mouthSensitivity
        case mouthCalibration
        case isMirrored
        case invertEyeX
        case invertEyeY
        case syncEyes
        case sendMergedEyes
        case language
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        host = try container.decodeIfPresent(String.self, forKey: .host) ?? host
        oscPort = try container.decodeIfPresent(Int.self, forKey: .oscPort) ?? oscPort
        vmcPort = try container.decodeIfPresent(Int.self, forKey: .vmcPort) ?? vmcPort
        faceDetectionConfidence = try container.decodeIfPresent(Float.self, forKey: .faceDetectionConfidence) ?? faceDetectionConfidence
        facePresenceConfidence = try container.decodeIfPresent(Float.self, forKey: .facePresenceConfidence) ?? facePresenceConfidence
        faceTrackingConfidence = try container.decodeIfPresent(Float.self, forKey: .faceTrackingConfidence) ?? faceTrackingConfidence
        eyeSensitivity = try container.decodeIfPresent(Float.self, forKey: .eyeSensitivity) ?? eyeSensitivity
        eyeCalibration = try container.decodeIfPresent(EyeCalibrationOffset.self, forKey: .eyeCalibration) ?? eyeCalibration
        eyeRangeCalibration = try container.decodeIfPresent(EyeRangeCalibration.self, forKey: .eyeRangeCalibration) ?? eyeRangeCalibration
        mouthSensitivity = try container.decodeIfPresent(Float.self, forKey: .mouthSensitivity) ?? mouthSensitivity
        mouthCalibration = try container.decodeIfPresent(MouthCalibrationOffset.self, forKey: .mouthCalibration) ?? mouthCalibration
        isMirrored = try container.decodeIfPresent(Bool.self, forKey: .isMirrored) ?? isMirrored
        invertEyeX = try container.decodeIfPresent(Bool.self, forKey: .invertEyeX) ?? invertEyeX
        invertEyeY = try container.decodeIfPresent(Bool.self, forKey: .invertEyeY) ?? invertEyeY
        syncEyes = try container.decodeIfPresent(Bool.self, forKey: .syncEyes) ?? syncEyes
        sendMergedEyes = try container.decodeIfPresent(Bool.self, forKey: .sendMergedEyes) ?? sendMergedEyes
        language = try container.decodeIfPresent(AppLanguage.self, forKey: .language) ?? language
    }
}

enum AppLanguage: String, Codable, Equatable {
    case english = "en"
    case chinese = "zh-Hans"

    var buttonTitle: String {
        switch self {
        case .english: return "EN"
        case .chinese: return "中文"
        }
    }

    var toggled: AppLanguage {
        switch self {
        case .english: return .chinese
        case .chinese: return .english
        }
    }
}

struct EyeCalibrationOffset: Codable, Equatable {
    var lookOutLeft: Float = 0
    var lookInLeft: Float = 0
    var lookInRight: Float = 0
    var lookOutRight: Float = 0
    var lookUpLeft: Float = 0
    var lookDownLeft: Float = 0
    var lookUpRight: Float = 0
    var lookDownRight: Float = 0

    var isCalibrated: Bool {
        lookOutLeft != 0 || lookInLeft != 0 ||
        lookInRight != 0 || lookOutRight != 0 ||
        lookUpLeft != 0 || lookDownLeft != 0 ||
        lookUpRight != 0 || lookDownRight != 0
    }
}

struct MouthCalibrationOffset: Codable, Equatable {
    var closedJawOpen: Float = 0
    var closedMouthClose: Float = 0
    var maxJawOpen: Float = 1
    var maxMouthClose: Float = 0

    var isCalibrated: Bool {
        closedJawOpen != 0 || maxJawOpen != 1
    }
}

struct EyeRangeCalibration: Codable, Equatable {
    var minValues: [String: Float] = [:]
    var maxValues: [String: Float] = [:]

    var hasMin: Bool { !minValues.isEmpty }
    var hasMax: Bool { !maxValues.isEmpty }
    var isCalibrated: Bool { hasMin && hasMax }
}

struct FaceFrame {
    var rawFaceData: [String: Float]
    var streamFaceData: [String: Float]
    var blendshapes: [String: Float]
    var timestamp: TimeInterval
}

enum TrackingStatus: Equatable {
    case idle
    case loadingModel
    case modelReady
    case cameraStopped
    case waitingForFace
    case faceDetected
    case streaming
    case error(String)
}

extension TrackingStatus {
    func text(language: AppLanguage) -> String {
        switch self {
        case .idle: return L.text(.idle, language)
        case .loadingModel: return L.text(.loadingModel, language)
        case .modelReady: return L.text(.modelReady, language)
        case .cameraStopped: return L.text(.cameraStopped, language)
        case .waitingForFace: return L.text(.waitingForFace, language)
        case .faceDetected: return L.text(.faceDetected, language)
        case .streaming: return L.text(.streaming, language)
        case .error(let message): return L.error(message, language)
        }
    }
}

enum LocalizedKey {
    case idle
    case loadingModel
    case modelReady
    case cameraStopped
    case waitingForFace
    case faceDetected
    case streaming
    case fps
    case mouth
    case eye
    case settings
    case network
    case oscHost
    case sensitivity
    case tracking
    case backend
    case backendCpu
    case mirrorPreviewInference
    case invertEyeX
    case invertEyeY
    case syncEyes
    case sendMergedEyes
    case calibration
    case pupil
    case calibrated
    case notCalibrated
    case center
    case reset
    case eyeRange
    case rangeMinMaxSet
    case rangeMinSet
    case rangeMaxSet
    case rangeNotSet
    case min
    case max
    case closed
    case open
    case realtime
    case raw
    case output
    case merged
    case jaw
    case defaultOscAddresses
    case loadedParamMap
    case cameraPermissionDenied
    case startCameraBeforeStreaming
    case streamingConnectionFailed
    case missingFaceLandmarkerModel
    case cannotConvertCameraFrame
    case noFaceDataForPupilCalibration
    case noFaceDataForEyeRangeMinimum
    case noFaceDataForEyeRangeMaximum
    case noFaceDataForMouthCalibration
    case noFaceDataForMouthMaximum
}

enum L {
    static func text(_ key: LocalizedKey, _ language: AppLanguage) -> String {
        switch language {
        case .english:
            switch key {
            case .idle: return "Idle"
            case .loadingModel: return "Loading model..."
            case .modelReady: return "Model ready"
            case .cameraStopped: return "Camera stopped"
            case .waitingForFace: return "Waiting for face..."
            case .faceDetected: return "Face detected"
            case .streaming: return "Streaming"
            case .fps: return "FPS"
            case .mouth: return "Mouth"
            case .eye: return "Eye"
            case .settings: return "Settings"
            case .network: return "Network"
            case .oscHost: return "OSC host"
            case .sensitivity: return "Sensitivity"
            case .tracking: return "Tracking"
            case .backend: return "Backend"
            case .backendCpu: return "MediaPipe Tasks Vision: CPU"
            case .mirrorPreviewInference: return "Mirror preview/inference"
            case .invertEyeX: return "Invert eye X"
            case .invertEyeY: return "Invert eye Y"
            case .syncEyes: return "Sync eyes"
            case .sendMergedEyes: return "Send merged eyes"
            case .calibration: return "Calibration"
            case .pupil: return "Pupil"
            case .calibrated: return "Calibrated"
            case .notCalibrated: return "Not calibrated"
            case .center: return "Center"
            case .reset: return "Reset"
            case .eyeRange: return "Eye Range"
            case .rangeMinMaxSet: return "Min and max set"
            case .rangeMinSet: return "Min set"
            case .rangeMaxSet: return "Max set"
            case .rangeNotSet: return "Not set"
            case .min: return "Min"
            case .max: return "Max"
            case .closed: return "Closed"
            case .open: return "Open"
            case .realtime: return "Realtime"
            case .raw: return "Raw"
            case .output: return "Out"
            case .merged: return "Merged"
            case .jaw: return "Jaw"
            case .defaultOscAddresses: return "Default OSC addresses"
            case .loadedParamMap: return "Loaded %d float, %d binary groups"
            case .cameraPermissionDenied: return "Camera permission denied"
            case .startCameraBeforeStreaming: return "Start camera before streaming"
            case .streamingConnectionFailed: return "Streaming connection failed"
            case .missingFaceLandmarkerModel: return "Missing face_landmarker.task in the app bundle"
            case .cannotConvertCameraFrame: return "Could not convert camera frame for MediaPipe"
            case .noFaceDataForPupilCalibration: return "No face data for pupil calibration"
            case .noFaceDataForEyeRangeMinimum: return "No face data for eye range minimum"
            case .noFaceDataForEyeRangeMaximum: return "No face data for eye range maximum"
            case .noFaceDataForMouthCalibration: return "No face data for mouth calibration"
            case .noFaceDataForMouthMaximum: return "No face data for mouth maximum"
            }
        case .chinese:
            switch key {
            case .idle: return "空闲"
            case .loadingModel: return "模型加载中..."
            case .modelReady: return "模型已就绪"
            case .cameraStopped: return "摄像头已关闭"
            case .waitingForFace: return "等待面部..."
            case .faceDetected: return "已检测到面部"
            case .streaming: return "推流中"
            case .fps: return "帧率"
            case .mouth: return "嘴部"
            case .eye: return "眼部"
            case .settings: return "设置"
            case .network: return "网络"
            case .oscHost: return "OSC 地址"
            case .sensitivity: return "灵敏度"
            case .tracking: return "追踪"
            case .backend: return "后端"
            case .backendCpu: return "MediaPipe Tasks Vision：CPU"
            case .mirrorPreviewInference: return "镜像预览/推理"
            case .invertEyeX: return "眼部 X 取反"
            case .invertEyeY: return "眼部 Y 取反"
            case .syncEyes: return "双眼同步"
            case .sendMergedEyes: return "发送合并眼部参数"
            case .calibration: return "校准"
            case .pupil: return "瞳孔"
            case .calibrated: return "已校准"
            case .notCalibrated: return "未校准"
            case .center: return "居中"
            case .reset: return "重置"
            case .eyeRange: return "眼部范围"
            case .rangeMinMaxSet: return "最小值和最大值已设置"
            case .rangeMinSet: return "已设置最小值"
            case .rangeMaxSet: return "已设置最大值"
            case .rangeNotSet: return "未设置"
            case .min: return "最小"
            case .max: return "最大"
            case .closed: return "闭合"
            case .open: return "张开"
            case .realtime: return "实时数据"
            case .raw: return "原始"
            case .output: return "输出"
            case .merged: return "合并"
            case .jaw: return "下颌"
            case .defaultOscAddresses: return "使用默认 OSC 地址"
            case .loadedParamMap: return "已加载 %d 个浮点参数，%d 个二进制组"
            case .cameraPermissionDenied: return "摄像头权限被拒绝"
            case .startCameraBeforeStreaming: return "请先开启摄像头再推流"
            case .streamingConnectionFailed: return "推流连接失败"
            case .missingFaceLandmarkerModel: return "应用包内缺少 face_landmarker.task 模型"
            case .cannotConvertCameraFrame: return "无法将摄像头画面转换给 MediaPipe"
            case .noFaceDataForPupilCalibration: return "没有可用于瞳孔校准的面部数据"
            case .noFaceDataForEyeRangeMinimum: return "没有可用于眼部范围最小值的面部数据"
            case .noFaceDataForEyeRangeMaximum: return "没有可用于眼部范围最大值的面部数据"
            case .noFaceDataForMouthCalibration: return "没有可用于嘴部校准的面部数据"
            case .noFaceDataForMouthMaximum: return "没有可用于嘴部最大值的面部数据"
            }
        }
    }

    static func format(_ key: LocalizedKey, _ language: AppLanguage, _ args: CVarArg...) -> String {
        String(format: text(key, language), arguments: args)
    }

    static func error(_ message: String, _ language: AppLanguage) -> String {
        let knownErrors: [String: LocalizedKey] = [
            "Camera permission denied": .cameraPermissionDenied,
            "Start camera before streaming": .startCameraBeforeStreaming,
            "Streaming connection failed": .streamingConnectionFailed,
            "Missing face_landmarker.task in the app bundle.": .missingFaceLandmarkerModel,
            "Could not convert camera frame for MediaPipe.": .cannotConvertCameraFrame,
            "No face data for pupil calibration": .noFaceDataForPupilCalibration,
            "No face data for eye range minimum": .noFaceDataForEyeRangeMinimum,
            "No face data for eye range maximum": .noFaceDataForEyeRangeMaximum,
            "No face data for mouth calibration": .noFaceDataForMouthCalibration,
            "No face data for mouth maximum": .noFaceDataForMouthMaximum
        ]
        guard let key = knownErrors[message] else { return message }
        return text(key, language)
    }
}

enum ConfigStore {
    private static let key = "FaceTrackStandalone.AppConfig"

    static func load() -> AppConfig {
        guard let data = UserDefaults.standard.data(forKey: key) else {
            return AppConfig()
        }
        return (try? JSONDecoder().decode(AppConfig.self, from: data)) ?? AppConfig()
    }

    static func save(_ config: AppConfig) {
        guard let data = try? JSONEncoder().encode(config) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
}

extension Float {
    func clamped(_ minValue: Float, _ maxValue: Float) -> Float {
        min(max(self, minValue), maxValue)
    }
}
