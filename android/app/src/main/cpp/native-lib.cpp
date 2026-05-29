#include <jni.h>
#include <android/log.h>
#include <string>
#include <sys/utsname.h>
#include <fstream>
#include <EGL/egl.h>
#include <GLES2/gl2.h>

#define LOG_TAG "FaceTrack-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * 通过 EGL 查询 GPU 渲染器名称
 * 通用方法，适用于所有 GPU (Adreno/Mali/Immortalis/Maleoon/PowerVR)
 */
static std::string queryGpuRendererViaEGL() {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        LOGE("Failed to get EGL display");
        return "unknown";
    }

    if (!eglInitialize(display, nullptr, nullptr)) {
        LOGE("Failed to initialize EGL");
        return "unknown";
    }

    // 配置 EGL
    EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(display, attribs, &config, 1, &numConfigs) || numConfigs == 0) {
        LOGE("Failed to choose EGL config");
        eglTerminate(display);
        return "unknown";
    }

    // 创建上下文
    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };

    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) {
        LOGE("Failed to create EGL context");
        eglTerminate(display);
        return "unknown";
    }

    // 创建 PBuffer Surface
    EGLint surfaceAttribs[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE
    };

    EGLSurface surface = eglCreatePbufferSurface(display, config, surfaceAttribs);
    if (surface == EGL_NO_SURFACE) {
        LOGE("Failed to create PBuffer surface");
        eglDestroyContext(display, context);
        eglTerminate(display);
        return "unknown";
    }

    // 绑定上下文
    if (!eglMakeCurrent(display, surface, surface, context)) {
        LOGE("Failed to make EGL context current");
        eglDestroySurface(display, surface);
        eglDestroyContext(display, context);
        eglTerminate(display);
        return "unknown";
    }

    // 查询 GPU 渲染器
    const char* renderer = reinterpret_cast<const char*>(glGetString(GL_RENDERER));
    std::string result = renderer ? renderer : "unknown";

    // 查询 OpenGL ES 版本
    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    LOGI("GPU Renderer: %s", result.c_str());
    LOGI("GL Version: %s", version ? version : "unknown");

    // 清理
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(display, surface);
    eglDestroyContext(display, context);
    eglTerminate(display);

    return result;
}

extern "C" {

/**
 * 获取 CPU 架构信息
 */
JNIEXPORT jstring JNICALL
Java_com_facetrack_standalone_NativeHelper_getCpuArch(JNIEnv *env, jclass clazz) {
    struct utsname uname_data;
    if (uname(&uname_data) == 0) {
        return env->NewStringUTF(uname_data.machine);
    }
    return env->NewStringUTF("unknown");
}

/**
 * 获取 GPU 渲染器信息
 * 优先通过 EGL 查询 (通用方法)，失败则读取 sysfs
 */
JNIEXPORT jstring JNICALL
Java_com_facetrack_standalone_NativeHelper_getGpuRenderer(JNIEnv *env, jclass clazz) {
    // 方法1: 通过 EGL/OpenGL ES 查询 (适用于所有 GPU)
    std::string eglRenderer = queryGpuRendererViaEGL();
    if (eglRenderer != "unknown") {
        return env->NewStringUTF(eglRenderer.c_str());
    }

    // 方法2: 高通 Adreno sysfs (仅骁龙设备)
    std::ifstream gpuinfo("/sys/class/kgsl/kgsl-3d0/gpuinfo");
    if (gpuinfo.is_open()) {
        std::string line;
        std::getline(gpuinfo, line);
        gpuinfo.close();
        if (!line.empty()) {
            return env->NewStringUTF(line.c_str());
        }
    }

    // 方法3: Mali sysfs (天玑/麒麟/展锐)
    std::ifstream maliInfo("/sys/class/misc/mali0/device/gpufreq");
    if (maliInfo.is_open()) {
        maliInfo.close();
        return env->NewStringUTF("Mali GPU");
    }

    // 方法4: 从 /proc/cpuinfo 读取 Hardware 字段
    std::ifstream device("/proc/cpuinfo");
    if (device.is_open()) {
        std::string line;
        while (std::getline(device, line)) {
            if (line.find("Hardware") != std::string::npos ||
                line.find("model name") != std::string::npos) {
                device.close();
                return env->NewStringUTF(line.substr(line.find(":") + 2).c_str());
            }
        }
        device.close();
    }

    return env->NewStringUTF("unknown");
}

/**
 * 获取 NNAPI 加速器信息
 */
JNIEXPORT jstring JNICALL
Java_com_facetrack_standalone_NativeHelper_getNpuInfo(JNIEnv *env, jclass clazz) {
    // 检查常见的 NPU 设备节点
    const char* npu_paths[] = {
        "/sys/class/npu/info",
        "/sys/class/huawei_npu/npu0/npu_name",
        "/sys/devices/system/cpu/cpu0/npu_name",
        "/sys/kernel/debug/huawei_npu/info"
    };

    for (const auto& path : npu_paths) {
        std::ifstream npu_file(path);
        if (npu_file.is_open()) {
            std::string line;
            std::getline(npu_file, line);
            npu_file.close();
            if (!line.empty()) {
                return env->NewStringUTF(line.c_str());
            }
        }
    }

    return env->NewStringUTF("NNAPI available (generic)");
}

/**
 * 检测设备支持的硬件加速类型
 * 返回位掩码: bit0=GPU, bit1=NNAPI, bit2=CPU
 */
JNIEXPORT jint JNICALL
Java_com_facetrack_standalone_NativeHelper_getAccelerationCaps(JNIEnv *env, jclass clazz) {
    int caps = 0;

    // GPU 检测: 通过 EGL 查询 GL_RENDERER
    std::string gpuRenderer = queryGpuRendererViaEGL();
    if (gpuRenderer != "unknown") {
        caps |= 0x01;  // GPU 可用
        LOGI("GPU detected via EGL: %s", gpuRenderer.c_str());
    } else {
        // 备用: 检查 sysfs
        std::ifstream gpu("/sys/class/kgsl/kgsl-3d0/gpuinfo");
        if (gpu.is_open()) {
            caps |= 0x01;
            gpu.close();
        } else {
            std::ifstream mali("/sys/class/misc/mali0/device/gpufreq");
            if (mali.is_open()) {
                caps |= 0x01;
                mali.close();
            }
        }
    }

    // NPU 检测
    std::ifstream npu("/sys/class/npu/info");
    if (npu.is_open()) {
        caps |= 0x02;
        npu.close();
    } else {
        std::ifstream huawei_npu("/sys/class/huawei_npu/npu0/npu_name");
        if (huawei_npu.is_open()) {
            caps |= 0x02;
            huawei_npu.close();
        }
    }

    // CPU 总是可用
    caps |= 0x04;

    LOGI("Acceleration caps: 0x%02X (GPU=%d, NPU=%d, CPU=%d)",
         caps, (caps & 0x01) != 0, (caps & 0x02) != 0, (caps & 0x04) != 0);
    return caps;
}

/**
 * 获取设备 SOC 型号描述
 */
JNIEXPORT jstring JNICALL
Java_com_facetrack_standalone_NativeHelper_getSocDescription(JNIEnv *env, jclass clazz) {
    std::string soc = "Unknown";

    // 读取 soc 型号
    std::ifstream soc_file("/sys/devices/soc0/soc_id");
    if (soc_file.is_open()) {
        std::getline(soc_file, soc);
        soc_file.close();
    }

    if (soc == "Unknown" || soc.empty()) {
        std::ifstream hw("/proc/cpuinfo");
        if (hw.is_open()) {
            std::string line;
            while (std::getline(hw, line)) {
                if (line.find("Hardware") != std::string::npos) {
                    soc = line.substr(line.find(":") + 2);
                    break;
                }
            }
            hw.close();
        }
    }

    return env->NewStringUTF(soc.c_str());
}

}  // extern "C"
