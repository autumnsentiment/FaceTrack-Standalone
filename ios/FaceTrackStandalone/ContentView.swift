import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var viewModel: FaceTrackViewModel

    var body: some View {
        ZStack {
            CameraPreviewView(
                session: viewModel.cameraService.session,
                isMirrored: viewModel.config.isMirrored
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                statusBar
                    .padding(.horizontal, 12)
                    .padding(.top, 10)
                Spacer()
                if viewModel.isSettingsOpen {
                    SettingsPanel()
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                } else if viewModel.isCalibrationOpen {
                    CalibrationPanel()
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
                controlBar
                    .padding(.horizontal, 14)
                    .padding(.bottom, 16)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.isSettingsOpen)
        .animation(.easeInOut(duration: 0.2), value: viewModel.isCalibrationOpen)
        .preferredColorScheme(.dark)
    }

    private var statusBar: some View {
        HStack(spacing: 10) {
            Text(statusText)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(2)
                .minimumScaleFactor(0.75)
            Spacer()
            Text(viewModel.backendLabel)
                .font(.system(size: 12, weight: .medium))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(.thinMaterial)
                .clipShape(Capsule())
        }
        .padding(10)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var statusText: String {
        let mouth = viewModel.lastStreamFaceData["v2/JawOpen"].map { String(format: "%.2f", $0) } ?? "-"
        let eyeXValue = viewModel.lastStreamFaceData["v2/EyesX"] ?? averageEye(axis: "X")
        let eyeYValue = viewModel.lastStreamFaceData["v2/EyesY"] ?? averageEye(axis: "Y")
        let eyeX = eyeXValue.map { String(format: "%.2f", $0) } ?? "-"
        let eyeY = eyeYValue.map { String(format: "%.2f", $0) } ?? "-"
        return "\(viewModel.status.text) | FPS \(viewModel.fps) | Mouth \(mouth) | Eye \(eyeX),\(eyeY)"
    }

    private var controlBar: some View {
        HStack(spacing: 14) {
            IconButton(systemName: viewModel.cameraService.isRunning ? "video.fill" : "video.slash.fill") {
                viewModel.toggleCamera()
            }
            IconButton(systemName: "arrow.triangle.2.circlepath.camera") {
                viewModel.switchCamera()
            }
            .disabled(!(viewModel.cameraService.hasFrontCamera && viewModel.cameraService.hasBackCamera))
            IconButton(systemName: viewModel.isStreaming ? "dot.radiowaves.left.and.right" : "antenna.radiowaves.left.and.right") {
                viewModel.toggleStreaming()
            }
            .disabled(!viewModel.cameraService.isRunning)
            Spacer()
            IconButton(systemName: "slider.horizontal.3") {
                viewModel.isCalibrationOpen = false
                viewModel.isSettingsOpen.toggle()
            }
            IconButton(systemName: "scope") {
                viewModel.isSettingsOpen = false
                viewModel.isCalibrationOpen.toggle()
            }
        }
        .padding(12)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func averageEye(axis: String) -> Float? {
        guard let left = viewModel.lastStreamFaceData["v2/EyeLeft\(axis)"],
              let right = viewModel.lastStreamFaceData["v2/EyeRight\(axis)"] else {
            return nil
        }
        return ((left + right) / 2).clamped(-1, 1)
    }
}

private struct IconButton: View {
    let systemName: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 18, weight: .semibold))
                .frame(width: 42, height: 42)
        }
        .buttonStyle(.plain)
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
