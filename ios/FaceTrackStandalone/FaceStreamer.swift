import Foundation
import Network

final class FaceStreamer {
    private static let vrcftAliases: [String: String] = [
        "v2/MouthSmileLeft": "v2/SmileFrownLeft",
        "v2/MouthSmileRight": "v2/SmileFrownRight",
        "v2/MouthFrownLeft": "v2/SmileFrownLeft",
        "v2/MouthFrownRight": "v2/SmileFrownRight",
        "v2/MouthLowerDownLeft": "v2/MouthLowerDown",
        "v2/MouthLowerDownRight": "v2/MouthLowerDown",
        "v2/MouthUpperUpLeft": "v2/MouthUpperUp",
        "v2/MouthUpperUpRight": "v2/MouthUpperUp",
        "v2/LipFunnel": "v2/LipFunnelUpper",
        "v2/LipPucker": "v2/LipPuckerUpper",
        "v2/MouthX": "v2/MouthUpperX",
        "v2/CheekPuffSuck": "v2/CheekPuffSuckRight",
        "v2/BrowLowererLeft": "v2/BrowDownLeft",
        "v2/BrowLowererRight": "v2/BrowDownRight",
        "v2/MouthRaiserLower": "v2/MouthLowerDown",
        "v2/MouthRaiserUpper": "v2/MouthUpperUp"
    ]

    private static let vrcftToVMCBlendshape: [String: String] = [
        "v2/JawOpen": "jaw_open",
        "v2/MouthClosed": "mouth_close",
        "v2/LipFunnel": "mouth_funnel",
        "v2/LipPucker": "mouth_pucker",
        "v2/MouthSmileLeft": "mouth_smile_left",
        "v2/MouthSmileRight": "mouth_smile_right",
        "v2/MouthOpen": "mouth_open",
        "v2/MouthSmile": "mouth_smile",
        "v2/EyeLidLeft": "eye_lid_left",
        "v2/EyeLidRight": "eye_lid_right",
        "v2/EyeSquintLeft": "eye_squint_left",
        "v2/EyeSquintRight": "eye_squint_right",
        "v2/EyeLeftX": "eye_look_left_x",
        "v2/EyeLeftY": "eye_look_left_y",
        "v2/EyeRightX": "eye_look_right_x",
        "v2/EyeRightY": "eye_look_right_y",
        "v2/EyesX": "eye_look_x",
        "v2/EyesY": "eye_look_y",
        "v2/BrowOuterUpLeft": "brow_outer_up_left",
        "v2/BrowOuterUpRight": "brow_outer_up_right",
        "v2/BrowLowererLeft": "brow_lowerer_left",
        "v2/BrowLowererRight": "brow_lowerer_right",
        "v2/BrowInnerUpLeft": "brow_inner_up_left",
        "v2/BrowInnerUpRight": "brow_inner_up_right",
        "v2/NoseSneerLeft": "nose_sneer_left",
        "v2/NoseSneerRight": "nose_sneer_right",
        "v2/CheekSquintLeft": "cheek_squint_left",
        "v2/CheekSquintRight": "cheek_squint_right",
        "v2/CheekPuffSuck": "cheek_puff",
        "v2/TongueOut": "tongue_out"
    ]

    private var oscConnection: NWConnection?
    private var vmcConnection: NWConnection?
    private var queue = DispatchQueue(label: "FaceStreamer.queue")
    private var vrcftParamMap: [String: String] = [:]
    private var binaryParamMap: [String: [Int: String]] = [:]
    private var useCustomMapping = false

    var isConnected: Bool {
        oscConnection != nil && vmcConnection != nil
    }

    func connect(host: String, oscPort: Int, vmcPort: Int) -> Bool {
        disconnect()
        guard let oscNWPort = NWEndpoint.Port(rawValue: UInt16(oscPort)),
              let vmcNWPort = NWEndpoint.Port(rawValue: UInt16(vmcPort)) else {
            return false
        }
        let endpointHost = NWEndpoint.Host(host)
        let osc = NWConnection(host: endpointHost, port: oscNWPort, using: .udp)
        let vmc = NWConnection(host: endpointHost, port: vmcNWPort, using: .udp)
        osc.start(queue: queue)
        vmc.start(queue: queue)
        oscConnection = osc
        vmcConnection = vmc
        return true
    }

    func disconnect() {
        oscConnection?.cancel()
        vmcConnection?.cancel()
        oscConnection = nil
        vmcConnection = nil
    }

    func updateParamMap(floatMap: [String: String], binaryMap: [String: [Int: String]]) {
        vrcftParamMap = floatMap
        binaryParamMap = binaryMap
        useCustomMapping = !floatMap.isEmpty
    }

    func sendFaceData(_ faceData: [String: Float]) {
        guard isConnected else { return }
        sendOSCFaceParams(faceData)
        sendVMCFace(faceData)
    }

    private func sendOSCFaceParams(_ faceData: [String: Float]) {
        guard let connection = oscConnection else { return }
        var messages: [Data] = []

        for (paramName, value) in faceData where paramName.hasPrefix("v2/") {
            let clampedValue = value.clamped(-1, 1)
            for address in resolveOscAddresses(paramName) {
                messages.append(OSCMessage.float(address: address, value: clampedValue))
            }
            for (address, boolValue) in resolveBinaryAddresses(paramName, value: clampedValue) {
                messages.append(OSCMessage.int(address: address, value: Int32(boolValue)))
            }
        }

        for message in messages {
            connection.send(content: message, completion: .contentProcessed { _ in })
        }
    }

    private func sendVMCFace(_ faceData: [String: Float]) {
        guard let connection = vmcConnection else { return }

        for (paramName, value) in faceData {
            guard let vmcName = Self.vrcftToVMCBlendshape[paramName] else { continue }
            let message = OSCMessage.vmcBlend(name: vmcName, value: value.clamped(-1, 1))
            connection.send(content: message, completion: .contentProcessed { _ in })
        }

        connection.send(content: OSCMessage.noArguments(address: "/VMC/Ext/Blend/Apply"), completion: .contentProcessed { _ in })
        connection.send(content: OSCMessage.int(address: "/VMC/Ext/OK", value: 1), completion: .contentProcessed { _ in })
    }

    private func resolveOscAddresses(_ vrcftName: String) -> [String] {
        var addresses: [String] = []

        if useCustomMapping {
            if let direct = vrcftParamMap[vrcftName] {
                addresses.append(direct)
            }
            if let aliasName = Self.vrcftAliases[vrcftName],
               aliasName != vrcftName,
               let aliasAddress = vrcftParamMap[aliasName],
               !addresses.contains(aliasAddress) {
                addresses.append(aliasAddress)
            }
        }

        if addresses.isEmpty {
            addresses.append("/avatar/parameters/\(vrcftName)")
        }

        let directEyeParams: Set<String> = [
            "v2/EyesX", "v2/EyesY",
            "v2/EyeLeftX", "v2/EyeLeftY", "v2/EyeRightX", "v2/EyeRightY"
        ]

        if directEyeParams.contains(vrcftName) {
            let shortAddress = "/avatar/parameters/\(vrcftName.replacingOccurrences(of: "v2/", with: ""))"
            if !addresses.contains(shortAddress) {
                addresses.append(shortAddress)
            }

            let eyeAliases: [String: [String]] = [
                "v2/EyesX": ["/avatar/parameters/EyeLookLeftRight", "/avatar/parameters/LookX"],
                "v2/EyesY": ["/avatar/parameters/EyeLookUpDown", "/avatar/parameters/LookY"],
                "v2/EyeLeftX": ["/avatar/parameters/LeftEyeX"],
                "v2/EyeLeftY": ["/avatar/parameters/LeftEyeY"],
                "v2/EyeRightX": ["/avatar/parameters/RightEyeX"],
                "v2/EyeRightY": ["/avatar/parameters/RightEyeY"]
            ]

            for alias in eyeAliases[vrcftName] ?? [] where !addresses.contains(alias) {
                addresses.append(alias)
            }
        }

        return addresses
    }

    private func resolveBinaryAddresses(_ vrcftName: String, value: Float) -> [(String, Int)] {
        guard useCustomMapping, !binaryParamMap.isEmpty else { return [] }

        var bits = binaryParamMap[vrcftName]
        if bits == nil, let aliasName = Self.vrcftAliases[vrcftName] {
            bits = binaryParamMap[aliasName]
        }
        if bits == nil {
            for (groupKey, groupBits) in binaryParamMap where groupKey.hasSuffix("/\(vrcftName)") {
                bits = groupBits
                break
            }
        }
        guard let bits = bits else { return [] }

        var result: [(String, Int)] = []
        let absValue: Float
        if let negativeAddress = bits[-1] {
            result.append((negativeAddress, value < 0 ? 1 : 0))
            absValue = Swift.abs(value)
        } else {
            absValue = max(0, value)
        }

        let maxBits = bits.keys.filter { $0 > 0 }.reduce(0, +)
        guard maxBits > 0 else { return result }
        let scaled = Int(absValue * Float(maxBits))

        for (bitValue, address) in bits where bitValue != -1 {
            result.append((address, (scaled & bitValue) != 0 ? 1 : 0))
        }

        return result
    }
}

enum ParamMapLoader {
    static func fetch(host: String) async -> ([String: String], [String: [Int: String]]) {
        guard let url = URL(string: "http://\(host):8900/param_map") else {
            return ([:], [:])
        }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                return ([:], [:])
            }
            let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            let floatMap = object?["float"] as? [String: String] ?? [:]
            var binaryMap: [String: [Int: String]] = [:]
            if let binaryObject = object?["binary"] as? [String: Any] {
                for (groupKey, value) in binaryObject {
                    guard let bitsObject = value as? [String: String] else { continue }
                    var bitsMap: [Int: String] = [:]
                    for (bitKey, address) in bitsObject {
                        if let bit = Int(bitKey) {
                            bitsMap[bit] = address
                        }
                    }
                    binaryMap[groupKey] = bitsMap
                }
            }
            return (floatMap, binaryMap)
        } catch {
            return ([:], [:])
        }
    }
}
