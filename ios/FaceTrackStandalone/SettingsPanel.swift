import SwiftUI

struct SettingsPanel: View {
    @EnvironmentObject private var viewModel: FaceTrackViewModel

    private var language: AppLanguage { viewModel.config.language }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header
                networkSection
                sensitivitySection
                togglesSection
                backendSection
            }
            .padding(14)
        }
        .frame(maxHeight: 390)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, 14)
        .padding(.bottom, 10)
    }

    private var header: some View {
        HStack {
            Label(L.text(.settings, language), systemImage: "slider.horizontal.3")
                .font(.headline)
            Spacer()
            Button {
                viewModel.isSettingsOpen = false
            } label: {
                Image(systemName: "xmark")
                    .frame(width: 30, height: 30)
            }
            .buttonStyle(.plain)
        }
    }

    private var networkSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L.text(.network, language))
                .font(.subheadline.weight(.semibold))
            TextField(L.text(.oscHost, language), text: binding(\.host))
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .textFieldStyle(.roundedBorder)
            HStack {
                PortField(title: "OSC", value: $viewModel.config.oscPort)
                PortField(title: "VMC", value: $viewModel.config.vmcPort)
            }
            Text(viewModel.paramMapStatus)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var sensitivitySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L.text(.sensitivity, language))
                .font(.subheadline.weight(.semibold))
            SliderRow(title: L.text(.eye, language), value: $viewModel.config.eyeSensitivity)
            SliderRow(title: L.text(.mouth, language), value: $viewModel.config.mouthSensitivity)
        }
    }

    private var togglesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L.text(.tracking, language))
                .font(.subheadline.weight(.semibold))
            Toggle(L.text(.mirrorPreviewInference, language), isOn: $viewModel.config.isMirrored)
            Toggle(L.text(.invertEyeX, language), isOn: $viewModel.config.invertEyeX)
            Toggle(L.text(.invertEyeY, language), isOn: $viewModel.config.invertEyeY)
            Toggle(L.text(.syncEyes, language), isOn: $viewModel.config.syncEyes)
            Toggle(L.text(.sendMergedEyes, language), isOn: $viewModel.config.sendMergedEyes)
        }
        .toggleStyle(.switch)
    }

    private var backendSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L.text(.backend, language))
                .font(.subheadline.weight(.semibold))
            HStack {
                Image(systemName: "cpu")
                Text(L.text(.backendCpu, language))
                Spacer()
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
    }

    private func binding(_ keyPath: WritableKeyPath<AppConfig, String>) -> Binding<String> {
        Binding(
            get: { viewModel.config[keyPath: keyPath] },
            set: { viewModel.config[keyPath: keyPath] = $0 }
        )
    }
}

private struct SliderRow: View {
    let title: String
    @Binding var value: Float

    var body: some View {
        HStack {
            Text(title)
                .frame(width: 54, alignment: .leading)
            Slider(
                value: Binding(
                    get: { Double(value) },
                    set: { value = Float($0) }
                ),
                in: 0.3...3.0,
                step: 0.1
            )
            Text(String(format: "%.1f", value))
                .font(.system(.body, design: .monospaced))
                .frame(width: 38, alignment: .trailing)
        }
    }
}

private struct PortField: View {
    let title: String
    @Binding var value: Int

    var body: some View {
        HStack {
            Text(title)
            TextField(title, value: $value, formatter: NumberFormatter.integerPort)
                .keyboardType(.numberPad)
                .textFieldStyle(.roundedBorder)
        }
    }
}

private extension NumberFormatter {
    static var integerPort: NumberFormatter {
        let formatter = NumberFormatter()
        formatter.numberStyle = .none
        formatter.minimum = 1
        formatter.maximum = 65535
        return formatter
    }
}
