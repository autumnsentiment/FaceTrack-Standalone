#!/usr/bin/env bash
# ========================================
#   FaceTrack-Standalone 启动脚本
# ========================================

# 检查 Python
if command -v python3 &>/dev/null; then
    PYTHON=python3
elif command -v python &>/dev/null; then
    PYTHON=python
else
    echo "[ERROR] Python 未安装"
    echo "请运行 ./install.sh 配置环境"
    exit 1
fi

$PYTHON main.py "$@"
