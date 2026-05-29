package com.facetrack.standalone

import android.os.Build
import android.util.Log

/**
 * 硬件检测与加速后端选择
 *
 * 支持平台:
 * - 骁龙8 Elite (SM8750) / 骁龙8 Gen 3 (SM8650): Adreno 830/750 GPU + Hexagon NPU
 * - 天玑9400+ / 天玑9300: Immortalis-G925/G720 GPU + APU 790
 * - 麒麟9010 / 麒麟9000S: Maleoon 910/Mali-G78 GPU + Da Vinci NPU
 * - 虎贲T610/T618/T7520: Mali-G52/Mali-G57 GPU (展锐/紫光)
 * - 其他 ARM64 设备: 通用兼容
 */
object NativeHelper {

    private const val TAG = "NativeHelper"

    const val CAP_GPU = 0x01
    const val CAP_NPU = 0x02
    const val CAP_CPU = 0x04

    var nativeLoaded = false
        private set

    init {
        try {
            System.loadLibrary("facetrack_standalone")
            nativeLoaded = true
            Log.d(TAG, "Native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library not found, using Java fallback")
        }
    }

    external fun getCpuArch(): String
    external fun getGpuRenderer(): String
    external fun getNpuInfo(): String
    external fun getAccelerationCaps(): Int
    external fun getSocDescription(): String

    /**
     * 获取设备硬件摘要
     */
    fun getDeviceSummary(): DeviceSummary {
        return try {
            DeviceSummary(
                cpuArch = if (nativeLoaded) getCpuArch() else Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                gpuRenderer = if (nativeLoaded) getGpuRenderer() else detectGpuFromBuild(),
                npuInfo = if (nativeLoaded) getNpuInfo() else "unknown",
                socDescription = if (nativeLoaded) getSocDescription() else detectSocFromBuild(),
                accelerationCaps = if (nativeLoaded) getAccelerationCaps() else detectCapsFromBuild(),
                androidVersion = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device info", e)
            DeviceSummary(
                cpuArch = "unknown", gpuRenderer = "unknown", npuInfo = "unknown",
                socDescription = "unknown", accelerationCaps = CAP_CPU,
                androidVersion = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER, model = Build.MODEL
            )
        }
    }

    // ===== Java 层降级检测 =====

    private fun detectSocFromBuild(): String {
        val soc = Build.SOC_MODEL ?: ""
        val hw = Build.HARDWARE ?: ""
        return if (soc.isNotEmpty()) soc else hw
    }

    private fun detectGpuFromBuild(): String {
        val soc = detectSocFromBuild().lowercase()
        return when {
            // 骁龙
            soc.contains("sm8750") || soc.contains("8 elite") -> "Adreno 830"
            soc.contains("sm8650") || soc.contains("8 gen 3") -> "Adreno 750"
            soc.contains("qcom") || soc.contains("qualcomm") -> "Adreno (Qualcomm)"
            // 天玑
            soc.contains("mt6989") || soc.contains("9400") -> "Immortalis-G925"
            soc.contains("mt6985") || soc.contains("9300") -> "Immortalis-G720"
            soc.contains("mt") -> "Mali/Immortalis (MediaTek)"
            // 麒麟
            soc.contains("9010") -> "Maleoon 910"
            soc.contains("9000s") -> "Mali-G78"
            // 展锐/紫光
            soc.contains("t610") || soc.contains("t618") -> "Mali-G52 MP2"
            soc.contains("t7520") || soc.contains("t760") || soc.contains("t770") -> "Mali-G57"
            soc.contains("t820") -> "Mali-G610"
            soc.contains("ud710") || soc.contains("ud715") -> "Mali-G57 (NPU)"
            soc.contains("unisoc") || soc.contains("spreadtrum") -> "Mali (Unisoc)"
            else -> "unknown"
        }
    }

    private fun detectCapsFromBuild(): Int {
        val soc = detectSocFromBuild().lowercase()
        var caps = CAP_CPU

        // 骁龙8 Elite / 8 Gen 3: GPU + NPU
        if (soc.contains("sm87") || soc.contains("sm86") ||
            soc.contains("8 elite") || soc.contains("8 gen 3") ||
            soc.contains("qualcomm") || soc.contains("qcom")) {
            caps = caps or CAP_GPU or CAP_NPU
        }
        // 天玑9400+ / 9300: GPU + NPU
        else if (soc.contains("mt6989") || soc.contains("mt6985") ||
                 soc.contains("9400") || soc.contains("9300") ||
                 soc.contains("mediatek")) {
            caps = caps or CAP_GPU or CAP_NPU
        }
        // 麒麟9010 / 9000S: GPU + NPU
        else if (soc.contains("9010") || soc.contains("9000s") ||
                 soc.contains("huawei") || soc.contains("hisilicon")) {
            caps = caps or CAP_GPU or CAP_NPU
        }
        // 展锐/紫光: T610/T618 仅 GPU, T7520/T820/UD7xx GPU+NPU
        else if (soc.contains("t610") || soc.contains("t618")) {
            caps = caps or CAP_GPU  // 虎贲T610/T618: Mali-G52, 无NPU
        }
        else if (soc.contains("t7520") || soc.contains("t760") || soc.contains("t770") ||
                 soc.contains("t820") || soc.contains("ud710") || soc.contains("ud715")) {
            caps = caps or CAP_GPU or CAP_NPU  // 新一代展锐有 NPU
        }
        else if (soc.contains("unisoc") || soc.contains("spreadtrum")) {
            caps = caps or CAP_GPU  // 通用展锐，至少有 GPU
        }
        // 通用: Android 12+ 设备基本都有 GPU
        else if (Build.VERSION.SDK_INT >= 31) {
            caps = caps or CAP_GPU
        }

        return caps
    }

    data class DeviceSummary(
        val cpuArch: String,
        val gpuRenderer: String,
        val npuInfo: String,
        val socDescription: String,
        val accelerationCaps: Int,
        val androidVersion: Int,
        val manufacturer: String,
        val model: String
    ) {
        val isQualcomm8Elite: Boolean
            get() = socDescription.contains("sm8750", ignoreCase = true) ||
                    socDescription.contains("8 elite", ignoreCase = true)

        val isQualcomm8Gen3: Boolean
            get() = socDescription.contains("sm8650", ignoreCase = true) ||
                    socDescription.contains("8 gen 3", ignoreCase = true)

        val isMediaTek9400: Boolean
            get() = socDescription.contains("mt6989", ignoreCase = true) ||
                    socDescription.contains("9400", ignoreCase = true)

        val isMediaTek9300: Boolean
            get() = socDescription.contains("mt6985", ignoreCase = true) ||
                    socDescription.contains("9300", ignoreCase = true)

        val isKirin9010: Boolean
            get() = socDescription.contains("9010", ignoreCase = true) ||
                    (manufacturer.equals("Huawei", ignoreCase = true) &&
                     socDescription.contains("9000", ignoreCase = true))

        val isUnisocT610: Boolean
            get() = socDescription.contains("t610", ignoreCase = true) ||
                    socDescription.contains("t618", ignoreCase = true)

        val isUnisocMid: Boolean
            get() = socDescription.contains("t7520", ignoreCase = true) ||
                    socDescription.contains("t760", ignoreCase = true) ||
                    socDescription.contains("t770", ignoreCase = true) ||
                    socDescription.contains("t820", ignoreCase = true)

        val isUnisoc: Boolean
            get() = isUnisocT610 || isUnisocMid ||
                    socDescription.contains("unisoc", ignoreCase = true) ||
                    socDescription.contains("spreadtrum", ignoreCase = true)

        val isFlagship2024: Boolean
            get() = isQualcomm8Elite || isQualcomm8Gen3 ||
                    isMediaTek9400 || isMediaTek9300 || isKirin9010

        val isLowEnd: Boolean
            get() = isUnisocT610

        fun describe(): String = buildString {
            append("设备: $manufacturer $model\n")
            append("SOC: $socDescription\n")
            append("GPU: $gpuRenderer\n")
            append("NPU: $npuInfo\n")
            append("旗舰: ${if (isFlagship2024) "是" else "否"}\n")
            if (isLowEnd) append("低端: 是\n")
            append("推荐加速: ${getRecommendedBackend().name}")
        }
    }

    enum class AccelerationBackend {
        AUTO,   // 自动选择: QNN > NNAPI > GPU > CPU
        GPU,    // MediaPipe GPU Delegate (OpenGL ES / OpenCL)
        NNAPI,  // NPU via NNAPI Delegate (实验性，MediaPipe 不直接支持，降级到 GPU)
        QNN,    // NPU via QNN Delegate (骁龙专用，需手动集成 QNN 库)
        CPU     // CPU 回退
    }

    /**
     * 解析 AUTO 后端为实际可用的后端
     * 优先级:
     * - 骁龙: QNN > GPU > CPU
     * - 天玑/麒麟 (Mali GPU): NNAPI > GPU > CPU (Mali GPU 兼容性差，优先 NNAPI)
     * - 展锐高端 (T7520/T820): NNAPI > GPU > CPU
     * - 展锐低端 (T610): CPU > GPU (Mali-G52 兼容性风险大)
     * - 其他: GPU > CPU
     */
    fun resolveAutoBackend(): AccelerationBackend {
        val caps = try {
            if (nativeLoaded) getAccelerationCaps() else detectCapsFromBuild()
        } catch (e: Exception) { CAP_CPU }

        val summary = getDeviceSummary()

        // 展锐低端 (T610/T618): Mali-G52 兼容性风险大，优先 CPU
        if (summary.isUnisocT610) {
            Log.i(TAG, "Unisoc T610 detected, preferring CPU for AUTO (Mali-G52 risky)")
            return AccelerationBackend.CPU
        }

        // 骁龙设备优先尝试 QNN
        if (isQnnAvailable()) return AccelerationBackend.QNN

        // Mali GPU (天玑/麒麟/展锐) 兼容性差，优先 NNAPI
        if (isMaliGpu() && (caps and CAP_NPU) != 0 && Build.VERSION.SDK_INT >= 27) {
            Log.i(TAG, "Mali GPU detected, preferring NNAPI over GPU for AUTO")
            return AccelerationBackend.NNAPI
        }

        // 其他 NPU 设备尝试 NNAPI
        if ((caps and CAP_NPU) != 0 && Build.VERSION.SDK_INT >= 27) {
            return AccelerationBackend.NNAPI
        }

        // GPU
        if ((caps and CAP_GPU) != 0) {
            return AccelerationBackend.GPU
        }

        return AccelerationBackend.CPU
    }

    /**
     * 推荐加速后端 (默认返回 AUTO)
     */
    fun getRecommendedBackend(): AccelerationBackend = AccelerationBackend.AUTO

    /**
     * 检测 QNN 库是否已集成
     */
    fun isQnnAvailable(): Boolean {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) return false

        val soc = detectSocFromBuild().lowercase()
        val isSnapdragon = soc.contains("sm87") || soc.contains("sm86") ||
                           soc.contains("8 elite") || soc.contains("8 gen 3") ||
                           soc.contains("qualcomm") || soc.contains("qcom")

        if (!isSnapdragon) return false

        return try {
            val qnnLibs = listOf("libQnnHtp.so", "libQnnSystem.so")
            val libDir = "/system/lib64/"
            qnnLibs.any { lib -> java.io.File(libDir + lib).exists() }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 运行时验证 NNAPI 是否可用
     */
    fun isNnapiAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < 27) return false
            val caps = getDeviceSummary().accelerationCaps
            (caps and CAP_NPU) != 0
        } catch (e: Exception) {
            Log.w(TAG, "NNAPI availability check failed", e)
            false
        }
    }

    /**
     * 运行时验证 GPU Delegate 是否可用
     * MediaPipe GPU Delegate 需要 OpenGL ES 3.1+
     */
    fun isGpuDelegateAvailable(): Boolean {
        return try {
            val caps = getDeviceSummary().accelerationCaps
            (caps and CAP_GPU) != 0
        } catch (e: Exception) {
            Log.w(TAG, "GPU availability check failed", e)
            false
        }
    }

    /**
     * 检测 GPU 是否为 Mali 系列 (天玑/麒麟/展锐等)
     */
    fun isMaliGpu(): Boolean {
        val gpu = getDeviceSummary().gpuRenderer.lowercase()
        return gpu.contains("mali") || gpu.contains("immortalis") || gpu.contains("maleoon")
    }

    /**
     * 检测 GPU 是否可能存在 MediaPipe 兼容性问题
     * - Mali GPU: compute shader 支持不完整
     * - 展锐低端 (Mali-G52): 驱动兼容性极差
     */
    fun isGpuCompatibilityRisky(): Boolean {
        val summary = getDeviceSummary()
        if (summary.isUnisocT610) return true
        return isMaliGpu()
    }

    /**
     * 获取所有可用后端列表
     */
    fun getAvailableBackends(): List<AccelerationBackend> {
        val backends = mutableListOf<AccelerationBackend>()
        backends.add(AccelerationBackend.AUTO)
        if (isGpuDelegateAvailable()) backends.add(AccelerationBackend.GPU)
        if (isNnapiAvailable()) backends.add(AccelerationBackend.NNAPI)
        if (isQnnAvailable()) backends.add(AccelerationBackend.QNN)
        backends.add(AccelerationBackend.CPU)
        return backends
    }
}
