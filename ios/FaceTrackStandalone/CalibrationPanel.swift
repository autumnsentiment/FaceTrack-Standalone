import SwiftUI

struct CalibrationPanel: View {
    @EnvironmentObject private var viewModel: FaceTrackViewModel

    private var language: AppLanguage { viewModel.config.language }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header
                pupilSection
                eyeRangeSection
                mouthSection
                realtimeSection
            }
            .padding(14)
        }
        .frame(maxHeight: 430)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, 14)
        .padding(.bottom, 10)
    }

    private var header: some View {
        HStack {
            Label(L.text(.calibration, language), systemImage: "scope")
                .font(.headline)
            Spacer()
            Button {
                viewModel.isCalibrationOpen = false
            } label: {
                Image(systemName: "xmark")
                    .frame(width: 30, height: 30)
            }
            .buttonStyle(.plain)
        }
    }

    private var pupilSection: some View {
        CalibrationSection(title: L.text(.pupil, language), status: viewModel.config.eyeCalibration.isCalibrated ? L.text(.calibrated, language) : L.text(.notCalibrated, language)) {
            ActionButton(title: L.text(.center, language), systemName: "plus.viewfinder") {
                viewModel.calibratePupil()
            }
            ActionButton(title: L.text(.reset, language), systemName: "arrow.counterclockwise") {
                viewModel.resetPupilCalibration()
            }
        }
    }

    private var eyeRangeSection: some View {
        CalibrationSection(title: L.text(.eyeRange, language), status: eyeRangeStatus) {
            ActionButton(title: L.text(.min, language), systemName: "arrow.down.left.and.arrow.up.right") {
                viewModel.setEyeRangeMin()
            }
            ActionButton(title: L.text(.max, language), systemName: "arrow.up.right.and.arrow.down.left") {
                viewModel.setEyeRangeMax()
            }
            ActionButton(title: L.text(.reset, language), systemName: "arrow.counterclockwise") {
                viewModel.resetEyeRange()
            }
        }
    }

    private var eyeRangeStatus: String {
        let range = viewModel.config.eyeRangeCalibration
        if range.isCalibrated { return L.text(.rangeMinMaxSet, language) }
        if range.hasMin { return L.text(.rangeMinSet, language) }
        if range.hasMax { return L.text(.rangeMaxSet, language) }
        return L.text(.rangeNotSet, language)
    }

    private var mouthSection: some View {
        CalibrationSection(title: L.text(.mouth, language), status: viewModel.config.mouthCalibration.isCalibrated ? L.text(.calibrated, language) : L.text(.notCalibrated, language)) {
            ActionButton(title: L.text(.closed, language), systemName: "mouth") {
                viewModel.calibrateMouthClosed()
            }
            ActionButton(title: L.text(.open, language), systemName: "mouth.fill") {
                viewModel.calibrateMouthMaxOpen()
            }
            ActionButton(title: L.text(.reset, language), systemName: "arrow.counterclockwise") {
                viewModel.resetMouthCalibration()
            }
        }
    }

    private var realtimeSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L.text(.realtime, language))
                .font(.subheadline.weight(.semibold))
            Text(realtimeText)
                .font(.system(size: 12, design: .monospaced))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(8)
                .background(.black.opacity(0.28))
                .clipShape(RoundedRectangle(cornerRadius: 6))
        }
    }

    private var realtimeText: String {
        let raw = viewModel.lastRawFaceData
        let stream = viewModel.lastStreamFaceData
        return [
            "\(L.text(.raw, language)) L \(format(raw["v2/EyeLeftX"])),\(format(raw["v2/EyeLeftY"])) R \(format(raw["v2/EyeRightX"])),\(format(raw["v2/EyeRightY"]))",
            "\(L.text(.output, language)) L \(format(stream["v2/EyeLeftX"])),\(format(stream["v2/EyeLeftY"])) R \(format(stream["v2/EyeRightX"])),\(format(stream["v2/EyeRightY"]))",
            "\(L.text(.merged, language)) \(format(stream["v2/EyesX"])),\(format(stream["v2/EyesY"])) \(L.text(.jaw, language)) \(format(stream["v2/JawOpen"]))"
        ].joined(separator: "\n")
    }

    private func format(_ value: Float?) -> String {
        value.map { String(format: "%.3f", $0) } ?? "-"
    }
}

private struct CalibrationSection<Content: View>: View {
    let title: String
    let status: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(status)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 8) {
                content
            }
        }
    }
}

private struct ActionButton: View {
    let title: String
    let systemName: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: systemName)
                .font(.caption.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
        }
        .buttonStyle(.plain)
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 7))
    }
}
