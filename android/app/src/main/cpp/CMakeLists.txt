# CMakeLists.txt for FaceTrack-Standalone Android
#
# 支持的硬件平台:
# - 骁龙8 Elite (SM8750): Adreno 830 GPU + Hexagon NPU
# - 天玑9400+ (MT6989): Immortalis-G925 GPU + APU 790 NPU
# - 麒麟9010: Maleoon 910 GPU + Da Vinci NPU
# - 虎贲T610/T618: Mali-G52 GPU (展锐)
# - 其他 ARM64 设备: 通用 armv8-a 基线

cmake_minimum_required(VERSION 3.18.1)

project("facetrack_standalone")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找系统库
find_library(log-lib log)
find_library(opengl-lib GLESv2)
find_library(egl-lib EGL)

# 构建输出
add_library(${CMAKE_PROJECT_NAME} SHARED
    native-lib.cpp
)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
    ${opengl-lib}
    ${egl-lib}
)

# 优化选项
if(CMAKE_BUILD_TYPE MATCHES "Release")
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -O3
        -ffast-math
        -funroll-loops
    )
endif()

# ARM64 架构配置
# 使用 armv8-a 基线确保兼容所有 ARM64 设备 (含虎贲T610等低端SOC)
# FP16/DOTPROD 等扩展通过 CPUID 运行时检测，在代码中条件执行
if(CMAKE_ANDROID_ARCH_ABI MATCHES "arm64-v8a")
    message(STATUS "Building for ARM64-v8a (baseline, compatible with all ARM64 SoCs)")

    # 基线: ARMv8-A (所有 ARM64 设备均支持)
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -march=armv8-a
        -mtune=generic
    )

    # 定义宏，让 C++ 代码知道编译时未启用 FP16/DOTPROD
    target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        ARM64_BASELINE_ONLY
    )
endif()
