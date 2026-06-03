"""
面部表情 VMC 推流模块

通过 VMC (Virtual Motion Capture) 协议发送面部表情数据。
VMC 协议基于 OSC，专门用于面部表情传输。

VMC 面部协议:
- /VMC/Ext/Blend/Val: 发送 blendshape 值
  格式: [name, value]
- /VMC/Ext/Blend/Apply: 应用所有 blendshape
- /VMC/Ext/OK: 可用信号

同时通过 OSC 发送 VRChat 面部参数 (VRCFT Unified Expressions v2):
- /avatar/parameters/XXX: VRCFT 标准参数

两种协议同时发送，确保兼容性:
- VMC 协议: 供 VMC 接收端使用 (如 VSeeFace, Luppet 等)
- VRChat OSC: 供 VRChat 直接使用
"""

from typing import Dict, List, Optional
import json
import os
import glob
import re
import time

from .base import BaseStreamer
from .osc_transport import OSCTransport


def _find_vrc_osc_dir() -> Optional[str]:
    base = os.path.expanduser(r"~\AppData\LocalLow\VRChat\VRChat\OSC")
    if not os.path.isdir(base):
        return None
    users = [d for d in os.listdir(base) if d.startswith("usr_")]
    if not users:
        return None
    return os.path.join(base, users[0], "Avatars")


def _load_avatar_param_map() -> Dict[str, str]:
    osc_dir = _find_vrc_osc_dir()
    if not osc_dir:
        return {}
    vrcft_to_addr = {}
    for json_path in glob.glob(os.path.join(osc_dir, "*.json")):
        try:
            with open(json_path, "r", encoding="utf-8-sig") as f:
                data = json.load(f)
        except (json.JSONDecodeError, OSError):
            continue
        for param in data.get("parameters", []):
            name = param.get("name", "")
            input_addr = param.get("input", {}).get("address", "")
            if not name or not input_addr:
                continue
            if "/v2/" not in name:
                continue
            parts = name.split("/")
            vrcft_key = "/".join(parts[-2:])
            if vrcft_key not in vrcft_to_addr:
                vrcft_to_addr[vrcft_key] = input_addr
    return vrcft_to_addr


def _load_avatar_binary_params() -> Dict[str, Dict[int, str]]:
    osc_dir = _find_vrc_osc_dir()
    if not osc_dir:
        return {}
    binary_groups = {}
    for json_path in glob.glob(os.path.join(osc_dir, "*.json")):
        try:
            with open(json_path, "r", encoding="utf-8-sig") as f:
                data = json.load(f)
        except (json.JSONDecodeError, OSError):
            continue
        for param in data.get("parameters", []):
            name = param.get("name", "")
            input_addr = param.get("input", {}).get("address", "")
            input_type = param.get("input", {}).get("type", "")
            if not name or not input_addr or input_type != "Bool":
                continue
            if "/v2/" not in name:
                continue
            m = re.match(r'^(.*/v2/\w+?)(\d+)$', name)
            if m:
                base_name = m.group(1)
                bit_val = int(m.group(2))
                if base_name not in binary_groups:
                    binary_groups[base_name] = {}
                binary_groups[base_name][bit_val] = input_addr
            neg_m = re.match(r'^(.*/v2/\w+?)Negative$', name)
            if neg_m:
                base_name = neg_m.group(1)
                if base_name not in binary_groups:
                    binary_groups[base_name] = {}
                binary_groups[base_name][-1] = input_addr
    return binary_groups


class FaceVMCStreamer(BaseStreamer):
    """面部表情 VMC 推流

    通过 VMC 协议和 VRChat OSC 同时发送面部表情数据。
    独立于身体和手部推流，可单独开关。
    """

    VRCFT_ALIASES = {
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
        "v2/JawX": "v2/JawX",
        "v2/CheekPuffSuck": "v2/CheekPuffSuckRight",
        "v2/BrowLowererLeft": "v2/BrowDownLeft",
        "v2/BrowLowererRight": "v2/BrowDownRight",
        "v2/MouthRaiserLower": "v2/MouthLowerDown",
        "v2/MouthRaiserUpper": "v2/MouthUpperUp",
    }

    # VRCFT v2 参数 -> VMC blendshape 名称映射
    VRCFT_TO_VMC_BLENDSHAPE = {
        "v2/MouthOpen": "mouth_open",
        "v2/MouthSmile": "mouth_smile",
        "v2/EyeOpenLeft": "eye_open_l",
        "v2/EyeOpenRight": "eye_open_r",
        "v2/BrowInnerUpLeft": "brow_inner_up_l",
        "v2/BrowInnerUpRight": "brow_inner_up_r",
        "v2/JawOpen": "jaw_open",
        "v2/MouthFunnel": "mouth_funnel",
        "v2/MouthPucker": "mouth_pucker",
        "v2/MouthLeft": "mouth_left",
        "v2/MouthRight": "mouth_right",
        "v2/MouthLowerDownLeft": "mouth_lower_down_l",
        "v2/MouthLowerDownRight": "mouth_lower_down_r",
        "v2/MouthUpperUpLeft": "mouth_upper_up_l",
        "v2/MouthUpperUpRight": "mouth_upper_up_r",
        "v2/EyeSquintLeft": "eye_squint_l",
        "v2/EyeSquintRight": "eye_squint_r",
        "v2/EyeWideLeft": "eye_wide_l",
        "v2/EyeWideRight": "eye_wide_r",
        "v2/BrowDownLeft": "brow_down_l",
        "v2/BrowDownRight": "brow_down_r",
        "v2/BrowOuterUpLeft": "brow_outer_up_l",
        "v2/BrowOuterUpRight": "brow_outer_up_r",
        "v2/NoseSneerLeft": "nose_sneer_l",
        "v2/NoseSneerRight": "nose_sneer_r",
        "v2/CheekPuff": "cheek_puff",
        "v2/CheekSquintLeft": "cheek_squint_l",
        "v2/CheekSquintRight": "cheek_squint_r",
        "v2/TongueOut": "tongue_out",
    }

    # 旧版自定义参数映射 (无 blendshapes 时回退)
    LEGACY_FACE_MAP = {
        "mouth_open": "/avatar/parameters/MouthOpenBlend",
        "mouth_wide": "/avatar/parameters/MouthSmileBlend",
        "left_eye_open": "/avatar/parameters/EyeOpenLeft",
        "right_eye_open": "/avatar/parameters/EyeOpenRight",
        "left_brow_up": "/avatar/parameters/BrowLeftUp",
        "right_brow_up": "/avatar/parameters/BrowRightUp",
    }

    def __init__(
        self,
        host: str = "127.0.0.1",
        port: int = 9000,
        vmc_port: int = 39539,
    ):
        super().__init__(name="face_vmc", host=host, port=port)
        self._osc = OSCTransport(host, port)
        self._vmc_osc = OSCTransport(host, vmc_port)
        self._vrcft_param_map: Dict[str, str] = {}
        self._binary_param_map: Dict[str, Dict[int, str]] = {}
        self._param_map_load_time: float = 0.0
        self._param_map_ttl: float = 30.0

    def _do_connect(self) -> bool:
        if not self._osc.connect():
            return False
        if not self._vmc_osc.connect():
            print("[face_vmc] WARNING: VMC socket init failed, VMC face transmission unavailable")
        self._refresh_param_map()
        return True

    def _do_disconnect(self):
        self._osc.disconnect()
        self._vmc_osc.disconnect()

    def _do_send(self, pose_data: Dict):
        """发送面部表情数据

        处理流程:
        1. 通过 VRChat OSC 发送面部参数 (VRCFT v2 或旧版)
        2. 通过 VMC 协议发送 blendshape 值
        """
        face_data = pose_data.get("face")
        if face_data is None:
            return

        # ===== VRChat OSC 面部参数 =====
        self._send_osc_face_params(face_data)

        # ===== VMC 面部表情 =====
        self._send_vmc_face(face_data)

    def _refresh_param_map(self):
        now = time.perf_counter()
        if now - self._param_map_load_time < self._param_map_ttl and self._vrcft_param_map:
            return
        new_map = _load_avatar_param_map()
        new_binary = _load_avatar_binary_params()
        if new_map:
            self._vrcft_param_map = new_map
            self._binary_param_map = new_binary
            self._param_map_load_time = now
            if not hasattr(self, '_param_map_logged') or not self._param_map_logged:
                print(f"[face_vmc] Loaded VRC OSC param map: {len(self._vrcft_param_map)} float, {len(self._binary_param_map)} binary groups")
                for k, v in list(self._vrcft_param_map.items())[:5]:
                    print(f"  {k} -> {v}")
                if len(self._vrcft_param_map) > 5:
                    print(f"  ... and {len(self._vrcft_param_map) - 5} more float params")
                for k, bits in list(self._binary_param_map.items())[:3]:
                    print(f"  {k} -> bits {sorted(bits.keys())}")
                if len(self._binary_param_map) > 3:
                    print(f"  ... and {len(self._binary_param_map) - 3} more binary groups")
                self._param_map_logged = True

    def _resolve_osc_address(self, vrcft_name: str) -> List[str]:
        addresses = []
        if self._vrcft_param_map:
            direct = self._vrcft_param_map.get(vrcft_name)
            if direct:
                addresses.append(direct)
            alias_name = self.VRCFT_ALIASES.get(vrcft_name)
            if alias_name and alias_name != vrcft_name:
                alias_addr = self._vrcft_param_map.get(alias_name)
                if alias_addr and alias_addr not in addresses:
                    addresses.append(alias_addr)
        if not addresses:
            addresses.append(f"/avatar/parameters/ft/f/{vrcft_name}")
        return addresses

    def _resolve_binary_addresses(self, vrcft_name: str, value: float) -> List[tuple]:
        if not self._binary_param_map:
            return []
        bits = self._binary_param_map.get(vrcft_name)
        if not bits:
            alias_name = self.VRCFT_ALIASES.get(vrcft_name)
            if alias_name:
                bits = self._binary_param_map.get(alias_name)
        if not bits:
            for group_key, group_bits in self._binary_param_map.items():
                if group_key.endswith("/" + vrcft_name):
                    bits = group_bits
                    break
        if not bits:
            return []
        result = []
        if -1 in bits:
            is_negative = value < 0
            result.append((bits[-1], 1 if is_negative else 0))
            abs_val = abs(value)
        else:
            abs_val = max(0.0, value)
        max_bits = sum(b for b in bits.keys() if b > 0)
        scaled = int(round(abs_val * max_bits))
        for bit_val, addr in bits.items():
            if bit_val == -1:
                continue
            is_set = bool(scaled & bit_val)
            result.append((addr, 1 if is_set else 0))
        return result

    def _send_osc_face_params(self, face_data: Dict):
        """通过 OSC 发送 VRChat 面部参数"""
        self._refresh_param_map()

        vrcft = face_data.get("vrcft")
        if vrcft:
            messages = []
            for param_name, value in vrcft.items():
                osc_addresses = self._resolve_osc_address(param_name)
                value = max(-1.0, min(1.0, float(value)))
                for addr in osc_addresses:
                    messages.append(OSCTransport.build_message(addr, value))
                binary_addrs = self._resolve_binary_addresses(param_name, value)
                for addr, bool_val in binary_addrs:
                    messages.append(OSCTransport.build_message(addr, bool_val))
            self._osc.send_batch(messages)
            return

        # 回退: 旧版自定义参数映射
        messages = []
        for param_name, osc_address in self.LEGACY_FACE_MAP.items():
            value = face_data.get(param_name, 0.0)
            value = max(0.0, min(1.0, float(value)))
            messages.append(OSCTransport.build_message(osc_address, value))

        # 眼睛闭合
        left_closed = 1.0 - face_data.get("left_eye_open", 1.0)
        right_closed = 1.0 - face_data.get("right_eye_open", 1.0)
        messages.append(OSCTransport.build_message(
            "/avatar/parameters/EyeLeftClosed", float(left_closed)
        ))
        messages.append(OSCTransport.build_message(
            "/avatar/parameters/EyeRightClosed", float(right_closed)
        ))

        self._osc.send_batch(messages)

    def _send_vmc_face(self, face_data: Dict):
        """通过 VMC 协议发送面部 blendshape"""
        if not self._vmc_osc.socket:
            return

        vrcft = face_data.get("vrcft")
        if not vrcft:
            # 无 VRCFT 数据时，从旧版参数构建简单的 VMC 消息
            self._send_vmc_legacy_face(face_data)
            return

        messages = []

        # 发送 VMC blendshape 值
        for vrcft_name, value in vrcft.items():
            vmc_name = self.VRCFT_TO_VMC_BLENDSHAPE.get(vrcft_name)
            if vmc_name:
                value = max(-1.0, min(1.0, float(value)))
                messages.append(OSCTransport.build_message(
                    "/VMC/Ext/Blend/Val", vmc_name, value
                ))

        # 应用所有 blendshape
        messages.append(OSCTransport.build_message("/VMC/Ext/Blend/Apply"))

        # 发送可用信号
        messages.append(OSCTransport.build_message("/VMC/Ext/OK", 1))

        self._vmc_osc.send_batch(messages)

    def _send_vmc_legacy_face(self, face_data: Dict):
        """通过 VMC 协议发送旧版面部参数"""
        if not self._vmc_osc.socket:
            return

        messages = []

        # 从旧版参数映射到 VMC blendshape
        legacy_to_vmc = {
            "mouth_open": "mouth_open",
            "mouth_wide": "mouth_smile",
            "left_eye_open": "eye_open_l",
            "right_eye_open": "eye_open_r",
            "left_brow_up": "brow_inner_up_l",
            "right_brow_up": "brow_inner_up_r",
        }

        for param_name, vmc_name in legacy_to_vmc.items():
            value = face_data.get(param_name, 0.0)
            value = max(0.0, min(1.0, float(value)))
            messages.append(OSCTransport.build_message(
                "/VMC/Ext/Blend/Val", vmc_name, value
            ))

        messages.append(OSCTransport.build_message("/VMC/Ext/Blend/Apply"))
        messages.append(OSCTransport.build_message("/VMC/Ext/OK", 1))

        self._vmc_osc.send_batch(messages)
