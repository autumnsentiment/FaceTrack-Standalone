#!/usr/bin/env python3
"""
FaceTrack-Standalone: 独立面部追踪系统

从 YOLO26n-VRC-Mocap 项目中提取的面部识别推理和推流逻辑，
可独立运行，无需身体/手部追踪模块。

功能:
- MediaPipe Face Landmarker 面部关键点检测
- VRCFT Unified Expressions v2 参数输出
- VMC 协议 + VRChat OSC 双通道推流
- 面部变换矩阵 (头部位置/旋转)
- 低光模式 / 校准 / 热更新配置

用法:
  python main.py [--config config.json] [--no-preview] [--low-light]
"""

import cv2
import json
import time
import argparse
import os
import sys
from typing import Dict, Optional

from face_landmark import FaceLandmarkDetector
from camera_capture import CameraCapture
from streamers.face_vmc import FaceVMCStreamer


class FaceTrackApp:
    """独立面部追踪应用"""

    def __init__(self, config_path: str = "config.json"):
        self.config_path = config_path
        self.config = self._load_config()

        self.camera = None
        self.face_detector = None
        self.face_streamer = None
        self.running = False
        self._low_light_mode = False

    def _load_config(self) -> Dict:
        """加载配置文件"""
        if os.path.exists(self.config_path):
            with open(self.config_path, "r", encoding="utf-8") as f:
                return json.load(f)
        print(f"[Config] 配置文件 {self.config_path} 不存在，使用默认配置")
        return self._default_config()

    def _default_config(self) -> Dict:
        """默认配置"""
        return {
            "camera": {
                "camera_id": 0,
                "width": 640,
                "height": 480,
                "fps": 30,
                "flip_horizontal": True
            },
            "face": {
                "enabled": True,
                "min_detection_confidence": 0.5,
                "min_tracking_confidence": 0.5,
                "min_face_presence_confidence": 0.5,
                "smoothing": "one_euro",
                "smoothing_factor": 0.3,
                "one_euro_min_cutoff": 1.0,
                "one_euro_beta": 0.007,
                "blendshape_smoothing": 0.4,
                "head_position_scale": 1.0,
                "head_position_offset": [0, 0, 0],
                "head_rotation_smoothing": 0.3
            },
            "streaming": {
                "host": "127.0.0.1",
                "osc_port": 9000,
                "vmc_port": 39539,
                "enabled": True
            },
            "display": {
                "show_preview": True,
                "window_name": "FaceTrack-Standalone"
            }
        }

    def _save_config(self):
        """保存配置文件"""
        with open(self.config_path, "w", encoding="utf-8") as f:
            json.dump(self.config, f, indent=2, ensure_ascii=False)

    def initialize(self) -> bool:
        """初始化所有模块"""
        print("=" * 50)
        print("  FaceTrack-Standalone - 独立面部追踪系统")
        print("=" * 50)

        # 1. 初始化摄像头
        cam_cfg = self.config.get("camera", {})
        self.camera = CameraCapture(
            camera_id=cam_cfg.get("camera_id", 0),
            width=cam_cfg.get("width", 640),
            height=cam_cfg.get("height", 480),
            fps=cam_cfg.get("fps", 30),
            flip_horizontal=cam_cfg.get("flip_horizontal", True),
        )
        if not self.camera.open():
            print("[Error] 无法打开摄像头")
            return False
        if not self.camera.start():
            print("[Error] 无法启动摄像头捕获")
            return False
        print(f"[Camera] 已启动 (ID={cam_cfg.get('camera_id', 0)})")

        # 2. 初始化面部检测器
        face_cfg = self.config.get("face", {})
        self.face_detector = FaceLandmarkDetector(
            min_detection_confidence=face_cfg.get("min_detection_confidence", 0.5),
            min_face_presence_confidence=face_cfg.get("min_face_presence_confidence", 0.5),
            min_tracking_confidence=face_cfg.get("min_tracking_confidence", 0.5),
            smoothing=face_cfg.get("smoothing", "one_euro"),
            smoothing_factor=face_cfg.get("smoothing_factor", 0.3),
            one_euro_min_cutoff=face_cfg.get("one_euro_min_cutoff", 1.0),
            one_euro_beta=face_cfg.get("one_euro_beta", 0.007),
            blendshape_smoothing=face_cfg.get("blendshape_smoothing", 0.4),
            head_position_scale=face_cfg.get("head_position_scale", 1.0),
            head_position_offset=face_cfg.get("head_position_offset", [0, 0, 0]),
            head_rotation_smoothing=face_cfg.get("head_rotation_smoothing", 0.3),
        )
        if not self.face_detector.load():
            print("[Error] Face Landmarker 加载失败")
            print("  请确保 face_landmarker.task 模型文件存在于当前目录或上级目录")
            print("  下载地址: https://storage.googleapis.com/mediapipe-models/"
                  "face_landmarker/face_landmarker/float16/latest/face_landmarker.task")
            return False
        print("[Face] Face Landmarker 已加载")

        # 3. 初始化推流
        stream_cfg = self.config.get("streaming", {})
        self.face_streamer = FaceVMCStreamer(
            host=stream_cfg.get("host", "127.0.0.1"),
            port=stream_cfg.get("osc_port", 9000),
            vmc_port=stream_cfg.get("vmc_port", 39539),
        )
        self.face_streamer.enabled = stream_cfg.get("enabled", True)
        self.face_streamer.inference_enabled = True

        if self.face_streamer.connect():
            print(f"[Stream] 已连接 OSC:{stream_cfg.get('osc_port', 9000)} VMC:{stream_cfg.get('vmc_port', 39539)}")
        else:
            print("[Stream] WARNING: 推流连接失败，将重试...")

        print("-" * 50)
        print("  按 [f] 校准面部 | [l] 切换低光模式 | [q] 退出")
        print("-" * 50)
        return True

    def run(self):
        """主循环"""
        self.running = True
        frame_count = 0
        fps_start = time.perf_counter()
        avg_fps = 0.0
        face_detected_count = 0

        while self.running:
            # 获取帧
            frame = self.camera.get_frame()
            if frame is None:
                time.sleep(0.01)
                continue

            frame_count += 1

            # 面部推理
            face_data = None
            if self.face_detector:
                face_data = self.face_detector.detect(frame)

            if face_data is not None:
                face_detected_count += 1

            # 推流
            if face_data is not None and self.face_streamer:
                pose_data = {"face": face_data}
                self.face_streamer.send(pose_data)

            # FPS 计算
            now = time.perf_counter()
            elapsed = now - fps_start
            if elapsed >= 1.0:
                avg_fps = frame_count / elapsed
                frame_count = 0
                face_detected_count = 0
                fps_start = now

            # 预览窗口
            display_cfg = self.config.get("display", {})
            if display_cfg.get("show_preview", True):
                display_frame = frame.copy()
                if face_data and self.face_detector:
                    display_frame = self.face_detector.draw_face_landmarks(display_frame, face_data)

                # 状态信息
                face_status = "ON" if face_data else "OFF"
                stream_status = "OK" if (self.face_streamer and self.face_streamer.connected) else "OFF"
                cv2.putText(display_frame, f"FPS: {avg_fps:.1f} | Face: {face_status} | Stream: {stream_status}",
                           (10, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 1, cv2.LINE_AA)

                if self._low_light_mode:
                    cv2.putText(display_frame, "LOW-LIGHT", (10, 45),
                               cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 200, 255), 1, cv2.LINE_AA)

                cv2.imshow(display_cfg.get("window_name", "FaceTrack-Standalone"), display_frame)

                # 键盘事件
                key = cv2.waitKey(1) & 0xFF
                if key == ord('q') or key == 27:  # q 或 ESC
                    self.running = False
                elif key == ord('f'):
                    if self.face_detector and face_data:
                        self.face_detector.calibrate(face_data)
                elif key == ord('l'):
                    self._low_light_mode = not self._low_light_mode
                    if self.face_detector:
                        self.face_detector.set_low_light_mode(self._low_light_mode)
            else:
                # 无预览模式，检查退出信号
                time.sleep(0.001)

            # 定期状态输出
            if frame_count % 300 == 0 and frame_count > 0:
                face_rate = (face_detected_count / frame_count) * 100 if frame_count > 0 else 0
                print(f"[Status] FPS: {avg_fps:.1f} | Face: {'ON' if face_data else 'OFF'} | "
                      f"Stream: {'OK' if (self.face_streamer and self.face_streamer.connected) else 'OFF'} | "
                      f"检测率: {face_rate:.0f}%")

    def stop(self):
        """停止所有模块"""
        self.running = False

        if self.face_streamer:
            self.face_streamer.disconnect()
            print("[Stream] 推流已断开")

        if self.face_detector:
            self.face_detector.release()
            print("[Face] Face Landmarker 已释放")

        if self.camera:
            self.camera.stop()
            print("[Camera] 摄像头已停止")

        cv2.destroyAllWindows()
        print("[App] 已退出")

    def hot_reload_config(self):
        """热更新配置（从文件重新读取）"""
        new_config = self._load_config()

        # 更新面部检测器参数
        if self.face_detector:
            face_cfg = new_config.get("face", {})
            if hasattr(self.face_detector, 'one_euro_min_cutoff'):
                self.face_detector.one_euro_min_cutoff = face_cfg.get("one_euro_min_cutoff", 1.0)
            if hasattr(self.face_detector, 'one_euro_beta'):
                self.face_detector.one_euro_beta = face_cfg.get("one_euro_beta", 0.007)
            if hasattr(self.face_detector, 'blendshape_smoothing'):
                self.face_detector.blendshape_smoothing = face_cfg.get("blendshape_smoothing", 0.4)
            if hasattr(self.face_detector, 'head_position_scale'):
                self.face_detector.head_position_scale = face_cfg.get("head_position_scale", 1.0)
            if hasattr(self.face_detector, 'head_rotation_smoothing'):
                self.face_detector.head_rotation_smoothing = face_cfg.get("head_rotation_smoothing", 0.3)

        # 更新推流参数
        if self.face_streamer:
            stream_cfg = new_config.get("streaming", {})
            self.face_streamer.enabled = stream_cfg.get("enabled", True)

        self.config = new_config
        print("[HotReload] 配置已热更新")


def main():
    parser = argparse.ArgumentParser(description="FaceTrack-Standalone: 独立面部追踪系统")
    parser.add_argument("--config", default="config.json", help="配置文件路径")
    parser.add_argument("--no-preview", action="store_true", help="禁用预览窗口")
    parser.add_argument("--low-light", action="store_true", help="启用低光模式")
    parser.add_argument("--camera", type=int, default=None, help="摄像头 ID")
    args = parser.parse_args()

    app = FaceTrackApp(config_path=args.config)

    # 命令行参数覆盖
    if args.no_preview:
        app.config.setdefault("display", {})["show_preview"] = False
    if args.low_light:
        app._low_light_mode = True
    if args.camera is not None:
        app.config.setdefault("camera", {})["camera_id"] = args.camera

    if not app.initialize():
        print("[Error] 初始化失败，请检查配置和依赖")
        sys.exit(1)

    # 低光模式
    if app._low_light_mode and app.face_detector:
        app.face_detector.set_low_light_mode(True)

    try:
        app.run()
    except KeyboardInterrupt:
        print("\n[App] 收到中断信号")
    finally:
        app.stop()


if __name__ == "__main__":
    main()
