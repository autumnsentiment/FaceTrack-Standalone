#include <jni.h>
#include <android/log.h>
#include <string>
#include <sys/utsname.h>
#include <fstream>

#define LOG_TAG "FaceTrack-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * 获取 CPU 架构信息
 * 用于识别处理器类型 (骁龙/联发科/麒麟)
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
 * 用于识别 GPU 类型 (Adreno/Mali)
 */
JNIEXPORT jstring JNICALL
Java_com_facetrack_standalone_NativeHelper_getGpuRenderer(JNIEnv *env, jclass clazz) {
    std::ifstream gpuinfo("/sys/class/kgsl/kgsl-3d0/gpuinfo");
    if (gpuinfo.is_open()) {
        std::string line;
        std::getline(gpuinfo, line);
        gpuinfo.close();
        return env->NewStringUTF(line.c_str());
    }
    
    // 备用方案: 读取设备信息
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
 * 用于识别 NPU 类型 (Hexagon/Da Vinci/APU)
 */
JNIEXPORT jstring JNICALL
Java_com_facetrack_standalone_NativeHelper_getNpuInfo(JNIEnv *env, jclass clazz) {
    // 读取 NNAPI 加速器列表
    std::ifstream npuinfo("/sys/class/npu/info");
    if (npuinfo.is_open()) {
        std::string line;
        std::getline(npuinfo, line);
        npuinfo.close();
        return env->NewStringUTF(line.c_str());
    }
    
    // 检查常见的 NPU 设备节点
    const char* npu_paths[] = {
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
            return env->NewStringUTF(line.c_str());
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
    int caps = 0;  // 0x01=GPU, 0x02=NNAPI, 0x04=CPU
    
    // 检查 /sys 目录中的加速器
    std::ifstream gpu("/sys/class/kgsl/kgsl-3d0/gpuinfo");
    if (gpu.is_open()) {
        caps |= 0x01;  // GPU 可用
        gpu.close();
    }
    
    // 检查 NNAPI
    std::ifstream npu("/sys/class/npu/info");
    if (npu.is_open()) {
        caps |= 0x02;  // NPU 可用
        npu.close();
    }
    
    // CPU 总是可用
    caps |= 0x04;
    
    LOGI("Acceleration caps: 0x%02X", caps);
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
    } else {
        // 备用: 读取 hardware
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