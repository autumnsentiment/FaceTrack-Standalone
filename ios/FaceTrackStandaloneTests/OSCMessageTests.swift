import XCTest
@testable import FaceTrackStandalone

final class OSCMessageTests: XCTestCase {
    func testFloatMessageUsesOscPaddingAndBigEndianFloat() {
        let data = OSCMessage.float(address: "/avatar/parameters/v2/JawOpen", value: 1.0)
        let bytes = [UInt8](data)

        XCTAssertEqual(String(bytes: bytes[0..<30], encoding: .utf8), "/avatar/parameters/v2/JawOpen")
        XCTAssertEqual(bytes[32], UInt8(ascii: ","))
        XCTAssertEqual(bytes[33], UInt8(ascii: "f"))
        XCTAssertEqual(Array(bytes.suffix(4)), [0x3F, 0x80, 0x00, 0x00])
    }

    func testVMCBlendMessageContainsAddressTypeNameAndValue() {
        let data = OSCMessage.vmcBlend(name: "jaw_open", value: 0.5)
        let bytes = [UInt8](data)

        XCTAssertTrue(data.count % 4 == 0)
        XCTAssertEqual(String(bytes: bytes[0..<18], encoding: .utf8), "/VMC/Ext/Blend/Val")
        XCTAssertTrue(bytes.contains(UInt8(ascii: "s")))
        XCTAssertEqual(Array(bytes.suffix(4)), [0x3F, 0x00, 0x00, 0x00])
    }
}
