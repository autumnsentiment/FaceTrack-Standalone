"""OSC 监听器 - 抓取 VRChat OSC 数据包"""
import socket
import struct
import time

def listen_osc(port=9000, duration=10):
    """监听 OSC 数据包并打印眼睛相关参数"""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("127.0.0.1", port))
    sock.settimeout(1.0)

    eye_params = {"EyeLeft", "EyeRight", "EyeLid", "EyeX", "EyeY", "EyesX", "EyesY"}
    start = time.time()
    count = 0

    print(f"Listening on 127.0.0.1:{port} for {duration}s...")
    print("Looking for eye-related OSC messages\n")

    while time.time() - start < duration:
        try:
            data, addr = sock.recvfrom(4096)
        except socket.timeout:
            continue

        # 简单解析 OSC
        try:
            # 找地址字符串
            addr_end = data.find(b'\x00')
            if addr_end < 0:
                continue
            address = data[:addr_end].decode('utf-8', errors='replace')

            # 检查是否是眼睛相关参数
            is_eye = any(k in address for k in eye_params)
            if not is_eye:
                continue

            # 找类型标签
            type_start = addr_end + 1
            while type_start < len(data) and data[type_start] == 0:
                type_start += 1
            if type_start >= len(data) or data[type_start] != ord(','):
                continue
            type_end = data.find(b'\x00', type_start)
            if type_end < 0:
                continue
            type_tags = data[type_start+1:type_end].decode('ascii', errors='replace')

            # 解析参数
            param_start = type_end + 1
            while param_start < len(data) and data[param_start] == 0:
                param_start += 1

            values = []
            for tag in type_tags:
                if tag == 'f':
                    if param_start + 4 > len(data):
                        break
                    val = struct.unpack('>f', data[param_start:param_start+4])[0]
                    values.append(f"{val:.4f}")
                    param_start += 4
                elif tag == 'i':
                    if param_start + 4 > len(data):
                        break
                    val = struct.unpack('>i', data[param_start:param_start+4])[0]
                    values.append(str(val))
                    param_start += 4

            print(f"  {address} [{type_tags}] = {', '.join(values)}")
            count += 1

        except Exception as e:
            pass

    sock.close()
    print(f"\nTotal eye-related messages: {count}")

if __name__ == "__main__":
    listen_osc(9000, 10)
