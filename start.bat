@echo off
chcp 65001 >nul
echo ========================================
echo   FaceTrack-Standalone
echo ========================================
echo.

REM 检查 Python
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python 未安装或不在 PATH 中
    echo 请运行 install.bat 配置环境
    pause
    exit /b 1
)

REM 启动面部追踪
python main.py %*

pause
