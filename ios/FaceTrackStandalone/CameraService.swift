import AVFoundation
import CoreMedia
import SwiftUI
import UIKit

final class CameraService: NSObject, ObservableObject {
    let session = AVCaptureSession()

    @Published private(set) var isRunning = false
    @Published private(set) var isFrontCamera = true
    @Published private(set) var hasFrontCamera = false
    @Published private(set) var hasBackCamera = false
    @Published private(set) var authorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)

    var onFrame: ((CMSampleBuffer, AVCaptureDevice.Position) -> Void)?

    private let sessionQueue = DispatchQueue(label: "CameraService.sessionQueue")
    private let videoOutputQueue = DispatchQueue(label: "CameraService.videoOutputQueue")
    private let videoOutput = AVCaptureVideoDataOutput()
    private var currentInput: AVCaptureDeviceInput?

    override init() {
        super.init()
        refreshCameraAvailability()
    }

    func requestPermissionIfNeeded() async -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        await MainActor.run {
            authorizationStatus = status
        }

        switch status {
        case .authorized:
            return true
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            await MainActor.run {
                authorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
            }
            return granted
        default:
            return false
        }
    }

    func start() {
        sessionQueue.async {
            guard !self.session.isRunning else { return }
            self.configureSession(position: self.isFrontCamera ? .front : .back)
            self.session.startRunning()
            DispatchQueue.main.async {
                self.isRunning = true
            }
        }
    }

    func stop() {
        sessionQueue.async {
            guard self.session.isRunning else { return }
            self.session.stopRunning()
            DispatchQueue.main.async {
                self.isRunning = false
            }
        }
    }

    func switchCamera() {
        sessionQueue.async {
            let nextPosition: AVCaptureDevice.Position = self.isFrontCamera ? .back : .front
            guard self.device(for: nextPosition) != nil else { return }
            self.configureSession(position: nextPosition)
            DispatchQueue.main.async {
                self.isFrontCamera = nextPosition == .front
            }
        }
    }

    private func configureSession(position: AVCaptureDevice.Position) {
        session.beginConfiguration()
        session.sessionPreset = .high

        if let currentInput {
            session.removeInput(currentInput)
            self.currentInput = nil
        }

        guard let camera = device(for: position),
              let input = try? AVCaptureDeviceInput(device: camera),
              session.canAddInput(input) else {
            session.commitConfiguration()
            return
        }

        session.addInput(input)
        currentInput = input

        if !session.outputs.contains(where: { $0 === videoOutput }) {
            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
            ]
            videoOutput.setSampleBufferDelegate(self, queue: videoOutputQueue)
            if session.canAddOutput(videoOutput) {
                session.addOutput(videoOutput)
            }
        }

        if let connection = videoOutput.connection(with: .video) {
            connection.videoOrientation = .portrait
            connection.isVideoMirrored = position == .front
        }

        session.commitConfiguration()
    }

    private func refreshCameraAvailability() {
        hasFrontCamera = device(for: .front) != nil
        hasBackCamera = device(for: .back) != nil
        isFrontCamera = hasFrontCamera
    }

    private func device(for position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position)
    }
}

extension CameraService: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        onFrame?(sampleBuffer, isFrontCamera ? .front : .back)
    }
}

struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession
    var isMirrored: Bool

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.videoPreviewLayer.session = session
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        uiView.transform = isMirrored ? CGAffineTransform(scaleX: -1, y: 1) : .identity
    }
}

final class PreviewView: UIView {
    override class var layerClass: AnyClass {
        AVCaptureVideoPreviewLayer.self
    }

    var videoPreviewLayer: AVCaptureVideoPreviewLayer {
        layer as! AVCaptureVideoPreviewLayer
    }
}
