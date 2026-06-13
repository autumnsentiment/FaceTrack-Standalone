import AVFoundation
import Foundation
import MediaPipeTasksVision
import UIKit

final class FaceLandmarkerService: NSObject {
    enum ServiceError: Error, LocalizedError {
        case missingModel
        case cannotCreateImage

        var errorDescription: String? {
            switch self {
            case .missingModel:
                return "Missing face_landmarker.task in the app bundle."
            case .cannotCreateImage:
                return "Could not convert camera frame for MediaPipe."
            }
        }
    }

    var onFrame: (([String: Float]) -> Void)?
    var onFaceMissing: (() -> Void)?
    var onError: ((Error) -> Void)?

    private var faceLandmarker: FaceLandmarker?

    func load(config: AppConfig) throws {
        guard let modelPath = Bundle.main.path(forResource: "face_landmarker", ofType: "task") else {
            throw ServiceError.missingModel
        }

        let baseOptions = BaseOptions()
        baseOptions.modelAssetPath = modelPath

        let options = FaceLandmarkerOptions()
        options.baseOptions = baseOptions
        options.runningMode = .liveStream
        options.numFaces = 1
        options.minFaceDetectionConfidence = config.faceDetectionConfidence
        options.minFacePresenceConfidence = config.facePresenceConfidence
        options.minTrackingConfidence = config.faceTrackingConfidence
        options.outputFaceBlendshapes = true
        options.outputFacialTransformationMatrixes = false
        options.faceLandmarkerLiveStreamDelegate = self

        faceLandmarker = try FaceLandmarker(options: options)
    }

    func close() {
        faceLandmarker = nil
    }

    func detect(sampleBuffer: CMSampleBuffer, mirrored: Bool) {
        do {
            let timestamp = Int(Date().timeIntervalSince1970 * 1000)
            let orientation: UIImage.Orientation = mirrored ? .upMirrored : .up
            let image = try MPImage(sampleBuffer: sampleBuffer, orientation: orientation)
            try faceLandmarker?.detectAsync(image: image, timestampInMilliseconds: timestamp)
        } catch {
            onError?(error)
        }
    }
}

extension FaceLandmarkerService: FaceLandmarkerLiveStreamDelegate {
    func faceLandmarker(
        _ faceLandmarker: FaceLandmarker,
        didFinishDetection result: FaceLandmarkerResult?,
        timestampInMilliseconds: Int,
        error: Error?
    ) {
        if let error = error {
            onError?(error)
            return
        }

        guard let result = result,
              let firstBlendshape = result.faceBlendshapes.first else {
            onFaceMissing?()
            return
        }

        var blendshapes: [String: Float] = [:]
        for category in firstBlendshape.categories {
            guard let name = category.categoryName else { continue }
            blendshapes[name] = category.score
        }
        onFrame?(blendshapes)
    }
}
