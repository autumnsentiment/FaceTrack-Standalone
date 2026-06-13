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
    var text: String {
        switch self {
        case .idle: return "Idle"
        case .loadingModel: return "Loading model..."
        case .modelReady: return "Model ready"
        case .cameraStopped: return "Camera stopped"
        case .waitingForFace: return "Waiting for face..."
        case .faceDetected: return "Face detected"
        case .streaming: return "Streaming"
        case .error(let message): return message
        }
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
