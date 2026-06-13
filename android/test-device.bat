@echo off
chcp 65001 >nul
echo ========================================
echo   FaceTrack-Standalone 设备兼容性测试
echo ========================================
echo.

REM 检查 ADB 连接
echo [1/4] 检查设备连接...
adb devices > temp_devices.txt
findstr /C:"device$" temp_devices.txt > nul
if errorlevel 1 (
    echo   [ERROR] 未检测到设备
    echo   请确保:
    echo   1. 手机已开启开发者模式
    echo   2. USB 调试已启用
    echo   3. 已授权本电脑调试
    del temp_devices.txt 2>nul
    pause
    exit /b 1
)

for /f "tokens=1" %%d in ('findstr /C:"device$" temp_devices.txt') do set DEVICE_ID=%%d
echo   [OK] 设备已连接: %DEVICE_ID%
del temp_devices.txt

REM 获取设备信息
echo.
echo [2/4] 获取设备信息...
echo   型号:
for /f "tokens=2 delims=:" %%a in ('adb -s %DEVICE_ID% shell getprop ro.product.model') do echo     %%a
echo   制造商:
for /f "tokens=2 delims=:" %%a in ('adb -s %DEVICE_ID% shell getprop ro.product.manufacturer') do echo     %%a
echo   Android 版本:
for /f "tokens=2 delims=:" %%a in ('adb -s %DEVICE_ID% shell getprop ro.build.version.release') do echo     %%a
echo   SOC:
for /f "tokens=2 delims=:" %%a in ('adb -s %DEVICE_ID% shell getprop ro.soc.model') do echo     %%a 2>nul
for /f "tokens=2 delims=:" %%a in ('adb -s %DEVICE_ID% shell getprop ro.hardware') do echo     (硬件: %%a)

REM 安装测试 APK
echo.
echo [3/4] 安装 FaceTrack-Standalone...
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK_PATH%" (
    echo   [ERROR] APK 未构建，请先运行 build-apk.bat
    pause
    exit /b 1
)

adb -s %DEVICE_ID% install -r "%APK_PATH%" > nul
if errorlevel 1 (
    echo   [ERROR] 安装失败
    pause
    exit /b 1
)
echo   [OK] 安装成功

REM 启动测试
echo.
echo [4/4] 启动测试...
echo   正在启动 FaceTrack-Standalone...
adb -s %DEVICE_ID% shell am start -n com.facetrack.standalone/.MainActivity

echo.
echo ========================================
echo   测试准备完成
echo ========================================
echo.
echo 请在手机上执行以下操作:
echo   1. 授予摄像头权限
echo   2. 点击"启动"按钮
echo   3. 将脸部对准摄像头
echo   4. 观察状态栏中的 FPS 和面部参数
echo.
echo 如需查看日志:
echo   adb -s %DEVICE_ID% logcat -s FaceTrack
echo.
pause