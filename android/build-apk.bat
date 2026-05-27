@echo off
chcp 65001 >nul
echo ========================================
echo   FaceTrack-Standalone Android 构建
echo ========================================
echo.

cd /d "%~dp0android"

REM 检查 gradlew
if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat 未找到
    echo 请先运行: android studio --import /path/to/FaceTrack-Standalone
    pause
    exit /b 1
)

REM 检查模型文件
echo [1/3] 检查模型文件...
if not exist "app\src\main\assets\face_landmarker.task" (
    echo   正在复制 face_landmarker.task 到 assets...
    if exist "..\face_landmarker.task" (
        xcopy /Y ..\face_landmarker.task app\src\main\assets\
        echo   [OK] 模型已复制
    ) else (
        echo   [ERROR] 未找到 face_landmarker.task
        echo   请将其复制到 FaceTrack-Standalone 目录
        pause
        exit /b 1
    )
) else (
    echo   [OK] 模型文件已存在
)

REM 构建 Debug APK
echo.
echo [2/3] 构建 APK...
gradlew.bat assembleDebug

if errorlevel 1 (
    echo [ERROR] 构建失败
    pause
    exit /b 1
)

echo   [OK] 构建完成

REM 输出 APK 位置
echo.
echo [3/3] APK 位置:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.

REM 自动安装到设备
echo 是否安装到设备? (y/n)
set /p install=
if /i "%install%"=="y" (
    echo 正在安装...
    ..\..\platform-tools\adb.exe install app\build\outputs\apk\debug\app-debug.apk
    if errorlevel 1 (
        echo [WARN] 安装失败，请检查设备连接
    ) else (
        echo [OK] 安装成功
    )
)

echo.
echo ========================================
echo   构建完成
echo ========================================
pause
