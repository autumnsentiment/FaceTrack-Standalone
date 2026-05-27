#!/usr/bin/env bash
# ========================================
#   FaceTrack-Standalone 环境配置
# ========================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "  ${GREEN}[OK]${NC}   $1"; }
miss() { echo -e "  ${YELLOW}[MISS]${NC} $1"; }
skip() { echo -e "  ${YELLOW}[SKIP]${NC} $1"; }
warn() { echo -e "  ${YELLOW}[WARN]${NC} $1"; }
fail() { echo -e "  ${RED}[FAIL]${NC} $1"; }

echo "========================================"
echo "  FaceTrack-Standalone 环境配置"
echo "========================================"
echo ""

# ===== 1. Python 检测 =====
echo "[1/5] 检测 Python..."
if ! command -v python3 &>/dev/null; then
    if ! command -v python &>/dev/null; then
        fail "Python 未安装"
        echo "  请安装 Python 3.8+: https://www.python.org/downloads/"
        exit 1
    fi
    PYTHON=python
else
    PYTHON=python3
fi

PYVER=$($PYTHON --version 2>&1 | awk '{print $2}')
ok "Python $PYVER"

if ! $PYTHON -c "import sys; exit(0 if sys.version_info >= (3, 8) else 1)" 2>/dev/null; then
    fail "Python 版本过低，需要 3.8+"
    exit 1
fi

# ===== 2. 核心依赖检测 =====
echo ""
echo "[2/5] 检测核心依赖..."

CORE_MISSING=0

if $PYTHON -c "import numpy" 2>/dev/null; then
    ok "numpy"
else
    miss "numpy"
    CORE_MISSING=1
fi

if $PYTHON -c "import cv2" 2>/dev/null; then
    ok "opencv-python"
else
    miss "opencv-python"
    CORE_MISSING=1
fi

if $PYTHON -c "import mediapipe" 2>/dev/null; then
    ok "mediapipe"
else
    miss "mediapipe"
    CORE_MISSING=1
fi

if $PYTHON -c "import scipy" 2>/dev/null; then
    ok "scipy"
else
    miss "scipy"
    CORE_MISSING=1
fi

if $PYTHON -c "from pythonosc import osc_message_builder" 2>/dev/null; then
    ok "python-osc"
else
    miss "python-osc"
    CORE_MISSING=1
fi

if [ $CORE_MISSING -eq 1 ]; then
    echo ""
    echo "  正在安装缺失的核心依赖..."
    $PYTHON -m pip install numpy opencv-python mediapipe scipy python-osc
    ok "核心依赖安装完成"
fi

# ===== 3. 模型文件检测 =====
echo ""
echo "[3/5] 检测模型文件..."

MODEL_FOUND=0
if [ -f "face_landmarker.task" ]; then
    ok "face_landmarker.task (当前目录)"
    MODEL_FOUND=1
elif [ -f "../face_landmarker.task" ]; then
    ok "face_landmarker.task (上级目录)"
    MODEL_FOUND=1
fi

if [ $MODEL_FOUND -eq 0 ]; then
    miss "face_landmarker.task"
    echo "  正在下载..."
    if $PYTHON -c "
import urllib.request
urllib.request.urlretrieve(
    'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task',
    'face_landmarker.task'
)
print('  [OK] 下载完成')
" 2>/dev/null; then
        :
    else
        warn "自动下载失败，请手动下载:"
        echo "  https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
    fi
fi

# ===== 4. 配置文件检测 =====
echo ""
echo "[4/5] 检测配置文件..."

if [ -f "config.json" ]; then
    ok "config.json"
    if $PYTHON -c "
import json
c = json.load(open('config.json', 'r', encoding='utf-8'))
assert 'camera' in c
assert 'face' in c
assert 'streaming' in c
" 2>/dev/null; then
        ok "配置结构完整"
    else
        warn "配置文件结构不完整，正在修复..."
        $PYTHON -c "
import json
c = json.load(open('config.json', 'r', encoding='utf-8'))
json.dump(c, open('config.json', 'w', encoding='utf-8'), indent=4, ensure_ascii=False)
print('  [OK] 配置已修复')
"
    fi
else
    miss "config.json，正在生成默认配置..."
    $PYTHON -c "
import json
config = {
    'camera': {'camera_id': 0, 'width': 640, 'height': 480, 'fps': 30, 'flip_horizontal': True},
    'face': {'enabled': True, 'min_detection_confidence': 0.5, 'min_tracking_confidence': 0.5, 'min_face_presence_confidence': 0.5, 'smoothing': 'one_euro', 'smoothing_factor': 0.3, 'one_euro_min_cutoff': 1.0, 'one_euro_beta': 0.007, 'blendshape_smoothing': 0.4, 'head_position_scale': 1.0, 'head_position_offset': [0, 0, 0], 'head_rotation_smoothing': 0.3},
    'streaming': {'host': '127.0.0.1', 'osc_port': 9000, 'vmc_port': 39539, 'enabled': True},
    'display': {'show_preview': True, 'window_name': 'FaceTrack-Standalone'}
}
json.dump(config, open('config.json', 'w', encoding='utf-8'), indent=4, ensure_ascii=False)
print('  [OK] 默认配置已生成')
"
fi

# ===== 5. 网络端口检测 =====
echo ""
echo "[5/5] 检测网络端口..."

check_udp_port() {
    if $PYTHON -c "
import socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(('0.0.0.0', $1))
s.close()
" 2>/dev/null; then
        ok "UDP $1 端口可用 ($2)"
    else
        warn "UDP $1 端口已被占用 ($2)"
    fi
}

check_udp_port 9000 "OSC 推流"
check_udp_port 39539 "VMC 推流"

# ===== 完成 =====
echo ""
echo "========================================"
echo "  环境配置完成"
echo "========================================"
echo ""
echo "启动方式:"
echo "  ./start.sh              - 启动面部追踪"
echo "  ./start.sh --no-preview - 无预览模式"
echo "  ./start.sh --low-light  - 低光模式"
echo ""
