import Foundation

enum FaceMapping {
    static let eyeRangeParams = [
        "v2/EyeLeftX", "v2/EyeLeftY",
        "v2/EyeRightX", "v2/EyeRightY",
        "v2/EyeLidLeft", "v2/EyeLidRight",
        "v2/EyeSquintLeft", "v2/EyeSquintRight"
    ]

    static func extractFaceData(
        blendshapes sourceBlendshapes: [String: Float],
        eyeSensitivity: Float,
        eyeCalibration: EyeCalibrationOffset,
        mouthSensitivity: Float,
        mouthCalibration: MouthCalibrationOffset,
        isMirrored: Bool,
        invertEyeX: Bool,
        invertEyeY: Bool,
        syncEyes: Bool,
        sendMergedEyes: Bool
    ) -> [String: Float] {
        var result: [String: Float] = [:]
        let bs = isMirrored ? mirroredBlendshapes(sourceBlendshapes) : sourceBlendshapes

        let rawJawOpen = bs["jawOpen", default: 0]
        let rawMouthClose = bs["mouthClose", default: 0]

        let calJawOpen: Float
        if mouthCalibration.isCalibrated {
            let range = mouthCalibration.maxJawOpen - mouthCalibration.closedJawOpen
            calJawOpen = range > 0.001 ? (rawJawOpen - mouthCalibration.closedJawOpen) / range : rawJawOpen
        } else {
            calJawOpen = rawJawOpen
        }

        let calMouthClose: Float
        if mouthCalibration.isCalibrated {
            let range = mouthCalibration.maxMouthClose - mouthCalibration.closedMouthClose
            calMouthClose = range > 0.001 ? (rawMouthClose - mouthCalibration.closedMouthClose) / range : rawMouthClose
        } else {
            calMouthClose = rawMouthClose
        }

        result["v2/JawOpen"] = (calJawOpen * mouthSensitivity).clamped(0, 1)
        result["v2/MouthClosed"] = (calMouthClose * mouthSensitivity).clamped(0, 1)
        result["v2/LipFunnel"] = (bs["mouthFunnel", default: 0] * mouthSensitivity).clamped(0, 1)
        result["v2/LipPucker"] = (bs["mouthPucker", default: 0] * mouthSensitivity).clamped(0, 1)
        result["v2/JawX"] = (bs["jawRight", default: 0] - bs["jawLeft", default: 0]) * mouthSensitivity
        result["v2/MouthX"] = (bs["mouthRight", default: 0] - bs["mouthLeft", default: 0]) * mouthSensitivity

        for name in [
            "MouthSmile", "MouthFrown", "MouthDimple", "MouthPress",
            "MouthStretch", "MouthLowerDown", "MouthUpperUp"
        ] {
            let lower = name.prefix(1).lowercased() + name.dropFirst()
            result["v2/\(name)Left"] = (bs["\(lower)Left", default: 0] * mouthSensitivity).clamped(0, 1)
            result["v2/\(name)Right"] = (bs["\(lower)Right", default: 0] * mouthSensitivity).clamped(0, 1)
        }

        result["v2/MouthRaiserLower"] = (bs["mouthRollLower", default: 0] * mouthSensitivity).clamped(0, 1)
        result["v2/MouthRaiserUpper"] = (bs["mouthRollUpper", default: 0] * mouthSensitivity).clamped(0, 1)

        for side in ["Left", "Right"] {
            let blink = bs["eyeBlink\(side)", default: 0] * eyeSensitivity
            let widen = bs["eyeWide\(side)", default: 0] * eyeSensitivity
            let openness = 1 - blink.clamped(0, 1)
            result["v2/EyeLid\(side)"] = (openness * 0.75 + widen.clamped(0, 1) * 0.25).clamped(0, 1)
        }
        result["v2/EyeSquintLeft"] = bs["eyeSquintLeft", default: 0] * eyeSensitivity
        result["v2/EyeSquintRight"] = bs["eyeSquintRight", default: 0] * eyeSensitivity

        let calLookOutLeft = bs["eyeLookOutLeft", default: 0] - eyeCalibration.lookOutLeft
        let calLookInLeft = bs["eyeLookInLeft", default: 0] - eyeCalibration.lookInLeft
        let calLookInRight = bs["eyeLookInRight", default: 0] - eyeCalibration.lookInRight
        let calLookOutRight = bs["eyeLookOutRight", default: 0] - eyeCalibration.lookOutRight
        let calLookUpLeft = bs["eyeLookUpLeft", default: 0] - eyeCalibration.lookUpLeft
        let calLookDownLeft = bs["eyeLookDownLeft", default: 0] - eyeCalibration.lookDownLeft
        let calLookUpRight = bs["eyeLookUpRight", default: 0] - eyeCalibration.lookUpRight
        let calLookDownRight = bs["eyeLookDownRight", default: 0] - eyeCalibration.lookDownRight

        result["v2/EyeLeftX"] = ((calLookInLeft - calLookOutLeft) * eyeSensitivity).clamped(-1, 1)
        result["v2/EyeRightX"] = ((calLookOutRight - calLookInRight) * eyeSensitivity).clamped(-1, 1)
        result["v2/EyeLeftY"] = ((calLookUpLeft - calLookDownLeft) * eyeSensitivity).clamped(-1, 1)
        result["v2/EyeRightY"] = ((calLookUpRight - calLookDownRight) * eyeSensitivity).clamped(-1, 1)

        if invertEyeX {
            result["v2/EyeLeftX"] = -(result["v2/EyeLeftX"] ?? 0)
            result["v2/EyeRightX"] = -(result["v2/EyeRightX"] ?? 0)
        }
        if invertEyeY {
            result["v2/EyeLeftY"] = -(result["v2/EyeLeftY"] ?? 0)
            result["v2/EyeRightY"] = -(result["v2/EyeRightY"] ?? 0)
        }

        if syncEyes {
            result["v2/EyeRightX"] = result["v2/EyeLeftX"] ?? 0
            result["v2/EyeRightY"] = result["v2/EyeLeftY"] ?? 0
            result["v2/EyeLidRight"] = result["v2/EyeLidLeft"] ?? 0
            result["v2/EyeSquintRight"] = result["v2/EyeSquintLeft"] ?? 0
        }

        if sendMergedEyes {
            result["v2/EyesX"] = (((result["v2/EyeLeftX"] ?? 0) + (result["v2/EyeRightX"] ?? 0)) / 2).clamped(-1, 1)
            result["v2/EyesY"] = (((result["v2/EyeLeftY"] ?? 0) + (result["v2/EyeRightY"] ?? 0)) / 2).clamped(-1, 1)
        }

        result["v2/BrowLowererLeft"] = bs["browDownLeft", default: 0]
        result["v2/BrowLowererRight"] = bs["browDownRight", default: 0]
        result["v2/BrowOuterUpLeft"] = bs["browOuterUpLeft", default: 0]
        result["v2/BrowOuterUpRight"] = bs["browOuterUpRight", default: 0]
        let browInner = bs["browInnerUp", default: 0]
        result["v2/BrowInnerUpLeft"] = browInner
        result["v2/BrowInnerUpRight"] = browInner
        result["v2/NoseSneerLeft"] = bs["noseSneerLeft", default: 0]
        result["v2/NoseSneerRight"] = bs["noseSneerRight", default: 0]
        result["v2/CheekSquintLeft"] = bs["cheekSquintLeft", default: 0]
        result["v2/CheekSquintRight"] = bs["cheekSquintRight", default: 0]
        result["v2/CheekPuffSuck"] = bs["cheekPuff", default: 0]
        result["v2/TongueOut"] = bs["tongueOut", default: 0]

        result["v2/MouthOpen"] =
            ((result["v2/MouthUpperUpLeft"] ?? 0) + (result["v2/MouthUpperUpRight"] ?? 0)) / 2 +
            ((result["v2/MouthLowerDownLeft"] ?? 0) + (result["v2/MouthLowerDownRight"] ?? 0)) / 2
        result["ExpressionTrackingActive"] = 1
        result["LipTrackingActive"] = 1

        return result
    }

    static func captureEyeRangeValues(_ faceData: [String: Float]) -> [String: Float] {
        Dictionary(uniqueKeysWithValues: eyeRangeParams.compactMap { key in
            faceData[key].map { (key, $0) }
        })
    }

    static func applyEyeRangeCalibration(
        _ faceData: [String: Float],
        calibration: EyeRangeCalibration
    ) -> [String: Float] {
        guard calibration.isCalibrated else { return faceData }
        var result = faceData

        for paramName in eyeRangeParams {
            guard let value = faceData[paramName],
                  let minValue = calibration.minValues[paramName],
                  let maxValue = calibration.maxValues[paramName] else {
                continue
            }
            result[paramName] = remapEyeValue(paramName: paramName, value: value, minValue: minValue, maxValue: maxValue)
        }

        if result["v2/EyesX"] != nil, let left = result["v2/EyeLeftX"], let right = result["v2/EyeRightX"] {
            result["v2/EyesX"] = ((left + right) / 2).clamped(-1, 1)
        }
        if result["v2/EyesY"] != nil, let left = result["v2/EyeLeftY"], let right = result["v2/EyeRightY"] {
            result["v2/EyesY"] = ((left + right) / 2).clamped(-1, 1)
        }

        return result
    }

    private static func mirroredBlendshapes(_ source: [String: Float]) -> [String: Float] {
        var mirrored = source
        let swapPairs = [
            ("eyeBlinkLeft", "eyeBlinkRight"),
            ("eyeWideLeft", "eyeWideRight"),
            ("eyeSquintLeft", "eyeSquintRight"),
            ("eyeLookOutLeft", "eyeLookInRight"),
            ("eyeLookInLeft", "eyeLookOutRight"),
            ("eyeLookUpLeft", "eyeLookUpRight"),
            ("eyeLookDownLeft", "eyeLookDownRight"),
            ("browDownLeft", "browDownRight"),
            ("browOuterUpLeft", "browOuterUpRight"),
            ("noseSneerLeft", "noseSneerRight"),
            ("cheekSquintLeft", "cheekSquintRight"),
            ("mouthSmileLeft", "mouthSmileRight"),
            ("mouthFrownLeft", "mouthFrownRight"),
            ("mouthDimpleLeft", "mouthDimpleRight"),
            ("mouthPressLeft", "mouthPressRight"),
            ("mouthStretchLeft", "mouthStretchRight"),
            ("mouthLowerDownLeft", "mouthLowerDownRight"),
            ("mouthUpperUpLeft", "mouthUpperUpRight"),
            ("jawLeft", "jawRight"),
            ("mouthLeft", "mouthRight")
        ]
        for (left, right) in swapPairs {
            mirrored[left] = source[right, default: 0]
            mirrored[right] = source[left, default: 0]
        }
        return mirrored
    }

    private static func remapEyeValue(paramName: String, value: Float, minValue: Float, maxValue: Float) -> Float {
        let targetMin: Float = (paramName.hasSuffix("X") || paramName.hasSuffix("Y")) ? -1 : 0
        let targetMax: Float = 1
        let range = maxValue - minValue
        if abs(range) <= 0.001 {
            return value.clamped(targetMin, targetMax)
        }
        let normalized = ((value - minValue) / range).clamped(0, 1)
        return (targetMin + normalized * (targetMax - targetMin)).clamped(targetMin, targetMax)
    }
}
