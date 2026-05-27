# CMakeLists.txt for FaceTrack-Standalone Android
#
# 支持的硬件加速:
# - 骁龙8 Elite (SM8750): Adreno 830 GPU + Hexagon NPU
# - 天玑9400+ (MT6989): Immortalis-G925 GPU + APU 790 NPU
# - 麒麟9010: Maleoon 910 GPU + Da Vinci NPU

cmake_minimum_required(VERSION 3.18.1)

project("facetrack_standalone")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找系统库
find_library(log-lib log)
find_library(opengl-lib GLESv2)

# 构建输出
add_library(${CMAKE_PROJECT_NAME} SHARED
    native-lib.cpp
)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
    ${opengl-lib}
)

# 优化选项
if(CMAKE_BUILD_TYPE MATCHES "Release")
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -O3
        -ffast-math
        -funroll-loops
    )
endif()

# ARM64 优化 (骁龙8 Elite / 天玑9400+ / 麒麟9010)
if(CMAKE_ANDROID_ARCH_ABI MATCHES "arm64-v8a")
    message(STATUS "Building for ARM64-v8a (Snapdragon 8 Elite / Dimensity 9400+ / Kirin 9010)")
    # ARMv8.2-A + FP16 + DOTPROD (骁龙8 Elite / 天玑9400+ 支持)
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -march=armv8.2-a+fp16+dotprod
        -mtune=cortex-a78
    )
endif()
