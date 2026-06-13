import SwiftUI

struct SettingsPanel: View {
    @EnvironmentObject private var viewModel: FaceTrackViewModel

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
            Label("Settings", systemImage: "slider.horizontal.3")
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
            Text("Network")
                .font(.subheadline.weight(.semibold))
            TextField("OSC host", text: binding(\.host))
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
            Text("Sensitivity")
                .font(.subheadline.weight(.semibold))
            SliderRow(title: "Eye", value: $viewModel.config.eyeSensitivity)
            SliderRow(title: "Mouth", value: $viewModel.config.mouthSensitivity)
        }
    }

    private var togglesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Tracking")
                .font(.subheadline.weight(.semibold))
            Toggle("Mirror preview/inference", isOn: $viewModel.config.isMirrored)
            Toggle("Invert eye X", isOn: $viewModel.config.invertEyeX)
            Toggle("Invert eye Y", isOn: $viewModel.config.invertEyeY)
            Toggle("Sync eyes", isOn: $viewModel.config.syncEyes)
            Toggle("Send merged eyes", isOn: $viewModel.config.sendMergedEyes)
        }
        .toggleStyle(.switch)
    }

    private var backendSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Backend")
                .font(.subheadline.weight(.semibold))
            HStack {
                Image(systemName: "cpu")
                Text("MediaPipe Tasks Vision: CPU")
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
