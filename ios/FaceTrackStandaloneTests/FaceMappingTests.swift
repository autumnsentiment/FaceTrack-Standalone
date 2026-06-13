import XCTest
@testable import FaceTrackStandalone

final class FaceMappingTests: XCTestCase {
    func testEyeSignAndMergedEyesMatchAndroidMapping() {
        let blendshapes: [String: Float] = [
            "eyeLookInLeft": 0.7,
            "eyeLookOutLeft": 0.2,
            "eyeLookOutRight": 0.8,
            "eyeLookInRight": 0.1,
            "eyeLookUpLeft": 0.6,
            "eyeLookDownLeft": 0.2,
            "eyeLookUpRight": 0.5,
            "eyeLookDownRight": 0.1
        ]

        let faceData = FaceMapping.extractFaceData(
            blendshapes: blendshapes,
            eyeSensitivity: 1,
            eyeCalibration: EyeCalibrationOffset(),
            mouthSensitivity: 1,
            mouthCalibration: MouthCalibrationOffset(),
            isMirrored: false,
            invertEyeX: false,
            invertEyeY: false,
            syncEyes: false,
            sendMergedEyes: true
        )

        XCTAssertEqual(faceData["v2/EyeLeftX"], 0.5, accuracy: 0.0001)
        XCTAssertEqual(faceData["v2/EyeRightX"], 0.7, accuracy: 0.0001)
        XCTAssertEqual(faceData["v2/EyesX"], 0.6, accuracy: 0.0001)
        XCTAssertEqual(faceData["v2/EyesY"], 0.4, accuracy: 0.0001)
    }

    func testSyncEyesCopiesLeftEyeToRightEye() {
        let blendshapes: [String: Float] = [
            "eyeLookInLeft": 0.4,
            "eyeLookOutLeft": 0.1,
            "eyeLookOutRight": 0.9,
            "eyeLookInRight": 0.2,
            "eyeBlinkLeft": 0.2,
            "eyeBlinkRight": 0.9
        ]

        let faceData = FaceMapping.extractFaceData(
            blendshapes: blendshapes,
            eyeSensitivity: 1,
            eyeCalibration: EyeCalibrationOffset(),
            mouthSensitivity: 1,
            mouthCalibration: MouthCalibrationOffset(),
            isMirrored: false,
            invertEyeX: false,
            invertEyeY: false,
            syncEyes: true,
            sendMergedEyes: false
        )

        XCTAssertEqual(faceData["v2/EyeRightX"], faceData["v2/EyeLeftX"])
        XCTAssertEqual(faceData["v2/EyeLidRight"], faceData["v2/EyeLidLeft"])
    }

    func testMouthCalibrationRemapsJawOpen() {
        let faceData = FaceMapping.extractFaceData(
            blendshapes: ["jawOpen": 0.4],
            eyeSensitivity: 1,
            eyeCalibration: EyeCalibrationOffset(),
            mouthSensitivity: 1,
            mouthCalibration: MouthCalibrationOffset(closedJawOpen: 0.2, maxJawOpen: 0.6),
            isMirrored: false,
            invertEyeX: false,
            invertEyeY: false,
            syncEyes: false,
            sendMergedEyes: false
        )

        XCTAssertEqual(faceData["v2/JawOpen"], 0.5, accuracy: 0.0001)
    }
}
