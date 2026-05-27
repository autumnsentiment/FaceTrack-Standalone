@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo   FaceTrack-Standalone 环境配置
echo ========================================
echo.

REM ===== 1. Python 检测 =====
echo [1/5] 检测 Python...
python --version >nul 2>&1
if errorlevel 1 (
    echo   [FAIL] Python 未安装或不在 PATH 中
    echo   请安装 Python 3.8+: https://www.python.org/downloads/
    goto :fail
)
for /f "tokens=2 delims= " %%v in ('python --version 2^>^&1') do set PYVER=%%v
echo   [OK] Python %PYVER%

REM Python 版本检查 (>=3.8)
python -c "import sys; exit(0 if sys.version_info >= (3, 8) else 1)" >nul 2>&1
if errorlevel 1 (
    echo   [FAIL] Python 版本过低，需要 3.8+
    goto :fail
)

REM ===== 2. 核心依赖检测 =====
echo.
echo [2/5] 检测核心依赖...

set CORE_MISSING=0

python -c "import numpy" >nul 2>&1
if errorlevel 1 (
    echo   [MISS] numpy
    set CORE_MISSING=1
) else (
    echo   [OK]   numpy
)

python -c "import cv2" >nul 2>&1
if errorlevel 1 (
    echo   [MISS] opencv-python
    set CORE_MISSING=1
) else (
    echo   [OK]   opencv-python
)

python -c "import mediapipe" >nul 2>&1
if errorlevel 1 (
    echo   [MISS] mediapipe
    set CORE_MISSING=1
) else (
    echo   [OK]   mediapipe
)

python -c "import scipy" >nul 2>&1
if errorlevel 1 (
    echo   [MISS] scipy
    set CORE_MISSING=1
) else (
    echo   [OK]   scipy
)

python -c "from pythonosc import osc_message_builder" >nul 2>&1
if errorlevel 1 (
    echo   [MISS] python-osc
    set CORE_MISSING=1
) else (
    echo   [OK]   python-osc
)

if %CORE_MISSING%==1 (
    echo.
    echo   正在安装缺失的核心依赖...
    pip install numpy opencv-python mediapipe scipy python-osc
    if errorlevel 1 (
        echo   [FAIL] 核心依赖安装失败
        goto :fail
    )
    echo   [OK] 核心依赖安装完成
)

REM ===== 3. 模型文件检测 =====
echo.
echo [3/5] 检测模型文件...

set MODEL_FOUND=0
if exist "face_landmarker.task" (
    set MODEL_FOUND=1
    echo   [OK]   face_landmarker.task (当前目录)
) else if exist "..\face_landmarker.task" (
    set MODEL_FOUND=1
    echo   [OK]   face_landmarker.task (上级目录)
)

if %MODEL_FOUND%==0 (
    echo   [MISS] face_landmarker.task
    echo   正在下载...
    python -c "import urllib.request; urllib.request.urlretrieve('https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task', 'face_landmarker.task'); print('  [OK] 下载完成')" 2>nul
    if not exist "face_landmarker.task" (
        echo   [FAIL] 自动下载失败，请手动下载:
        echo   https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
    )
)

REM ===== 4. 配置文件检测 =====
echo.
echo [4/5] 检测配置文件...

if exist "config.json" (
    echo   [OK]   config.json
    python -c "import json; c=json.load(open('config.json','r',encoding='utf-8')); assert 'camera' in c; assert 'face' in c; assert 'streaming' in c; print('  [OK] 配置结构完整')" 2>nul
    if errorlevel 1 (
        echo   [WARN] 配置文件结构不完整，正在修复...
        python -c "import json; c=json.load(open('config.json','r',encoding='utf-8')); json.dump(c,open('config.json','w',encoding='utf-8'),indent=4,ensure_ascii=False); print('  [OK] 配置已修复')"
    )
) else (
    echo   [MISS] config.json，正在生成默认配置...
    python -c "
import json
config = {
    'camera': {'camera_id': 0, 'width': 640, 'height': 480, 'fps': 30, 'flip_horizontal': True},
    'face': {'enabled': True, 'min_detection_confidence': 0.5, 'min_tracking_confidence': 0.5, 'min_face_presence_confidence': 0.5, 'smoothing': 'one_euro', 'smoothing_factor': 0.3, 'one_euro_min_cutoff': 1.0, 'one_euro_beta': 0.007, 'blendshape_smoothing': 0.4, 'head_position_scale': 1.0, 'head_position_offset': [0, 0, 0], 'head_rotation_smoothing': 0.3},
    'streaming': {'host': '127.0.0.1', 'osc_port': 9000, 'vmc_port': 39539, 'enabled': True},
    'display': {'show_preview': True, 'window_name': 'FaceTrack-Standalone'}
}
json.dump(config, open('config.json','w',encoding='utf-8'), indent=4, ensure_ascii=False)
print('  [OK] 默认配置已生成')
"
)

REM ===== 5. 网络端口检测 =====
echo.
echo [5/5] 检测网络端口...

python -c "import socket; s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.bind(('0.0.0.0',9000)); s.close(); print('  [OK]   UDP 9000 端口可用 (OSC)')" 2>nul
if errorlevel 1 (
    echo   [WARN] UDP 9000 端口已被占用 (OSC 推流)
)

python -c "import socket; s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.bind(('0.0.0.0',39539)); s.close(); print('  [OK]   UDP 39539 端口可用 (VMC)')" 2>nul
if errorlevel 1 (
    echo   [WARN] UDP 39539 端口已被占用 (VMC 推流)
)

REM ===== 完成 =====
echo.
echo ========================================
echo   环境配置完成
echo ========================================
echo.
echo 启动方式:
echo   start.bat              - 启动面部追踪
echo   start.bat --no-preview - 无预览模式
echo   start.bat --low-light  - 低光模式
echo.
goto :end

:fail
echo.
echo [FAIL] 环境配置失败，请检查上方错误信息
echo.

:end
pause
