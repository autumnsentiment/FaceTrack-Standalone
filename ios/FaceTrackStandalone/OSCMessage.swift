import Foundation

enum OSCMessage {
    static func float(address: String, value: Float) -> Data {
        var data = Data()
        data.append(padded(address))
        data.append(padded(",f"))
        data.append(bigEndian(value))
        return data
    }

    static func int(address: String, value: Int32) -> Data {
        var data = Data()
        data.append(padded(address))
        data.append(padded(",i"))
        data.append(bigEndian(value))
        return data
    }

    static func noArguments(address: String) -> Data {
        var data = Data()
        data.append(padded(address))
        data.append(padded(","))
        return data
    }

    static func vmcBlend(name: String, value: Float) -> Data {
        var data = Data()
        data.append(padded("/VMC/Ext/Blend/Val"))
        data.append(padded(",sf"))
        data.append(padded(name))
        data.append(bigEndian(value))
        return data
    }

    private static func padded(_ string: String) -> Data {
        var data = Data(string.utf8)
        data.append(0)
        while data.count % 4 != 0 {
            data.append(0)
        }
        return data
    }

    private static func bigEndian(_ value: Int32) -> Data {
        var bigEndian = value.bigEndian
        return Data(bytes: &bigEndian, count: MemoryLayout<Int32>.size)
    }

    private static func bigEndian(_ value: Float) -> Data {
        let bits = Int32(bitPattern: value.bitPattern)
        return bigEndian(bits)
    }
}
