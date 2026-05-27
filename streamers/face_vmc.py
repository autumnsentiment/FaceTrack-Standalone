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

from .base import BaseStreamer
from .osc_transport import OSCTransport


class FaceVMCStreamer(BaseStreamer):
    """面部表情 VMC 推流

    通过 VMC 协议和 VRChat OSC 同时发送面部表情数据。
    独立于身体和手部推流，可单独开关。
    """

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

    def _do_connect(self) -> bool:
        if not self._osc.connect():
            return False
        if not self._vmc_osc.connect():
            print("[face_vmc] WARNING: VMC socket init failed, VMC face transmission unavailable")
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

    def _send_osc_face_params(self, face_data: Dict):
        """通过 OSC 发送 VRChat 面部参数"""
        # 优先使用 VRCFT 标准参数
        vrcft = face_data.get("vrcft")
        if vrcft:
            messages = []
            for param_name, value in vrcft.items():
                osc_address = f"/avatar/parameters/{param_name}"
                value = max(-1.0, min(1.0, float(value)))
                messages.append(OSCTransport.build_message(osc_address, value))
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
