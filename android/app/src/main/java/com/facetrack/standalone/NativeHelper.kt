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

    /**
     * 推荐加速后端
     *
     * 优先级: NNAPI > GPU > CPU
     * 骁龙8 Elite / 天玑9400+ 的 NPU 性能极强，优先使用
     */
    fun getRecommendedBackend(): AccelerationBackend {
        val caps = try {
            if (nativeLoaded) getAccelerationCaps() else detectCapsFromBuild()
        } catch (e: Exception) { CAP_CPU }

        return when {
            (caps and CAP_NPU) != 0 -> AccelerationBackend.NNAPI
            (caps and CAP_GPU) != 0 -> AccelerationBackend.GPU
            else -> AccelerationBackend.CPU
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
            soc.contains("sm8750") || soc.contains("8 elite") -> "Adreno 830"
            soc.contains("sm8650") || soc.contains("8 gen 3") -> "Adreno 750"
            soc.contains("mt6989") || soc.contains("9400") -> "Immortalis-G925"
            soc.contains("mt6985") || soc.contains("9300") -> "Immortalis-G720"
            soc.contains("9010") -> "Maleoon 910"
            soc.contains("9000s") -> "Mali-G78"
            soc.contains("qcom") || soc.contains("qualcomm") -> "Adreno (Qualcomm)"
            soc.contains("mt") -> "Mali/Immortalis (MediaTek)"
            else -> "unknown"
        }
    }

    private fun detectCapsFromBuild(): Int {
        val soc = detectSocFromBuild().lowercase()
        var caps = CAP_CPU  // CPU 总是可用

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
        // 通用: 2024+ 旗舰芯片都有 GPU
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

        val isFlagship2024: Boolean
            get() = isQualcomm8Elite || isQualcomm8Gen3 ||
                    isMediaTek9400 || isMediaTek9300 || isKirin9010

        fun describe(): String = buildString {
            append("设备: $manufacturer $model\n")
            append("SOC: $socDescription\n")
            append("GPU: $gpuRenderer\n")
            append("NPU: $npuInfo\n")
            append("旗舰: ${if (isFlagship2024) "是" else "否"}\n")
            append("推荐加速: ${getRecommendedBackend().name}")
        }
    }

    enum class AccelerationBackend {
        GPU,
        NNAPI,
        CPU
    }
}
