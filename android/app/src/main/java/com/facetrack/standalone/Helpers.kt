package com.facetrack.standalone

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.components.containers.Category
import java.io.File
import java.io.FileOutputStream
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 配置类
 */
data class AppConfig(
    val host: String = "127.0.0.1",
    val oscPort: Int = 9000,
    val vmcPort: Int = 39539,
    val faceDetectionConfidence: Float = 0.5f,
    val facePresenceConfidence: Float = 0.5f,
    val faceTrackingConfidence: Float = 0.5f,
    val eyeSensitivity: Float = 1.0f,
    val eyeCalibration: EyeCalibrationOffset = EyeCalibrationOffset(),
    val mouthSensitivity: Float = 1.0f,
    val mouthCalibration: MouthCalibrationOffset = MouthCalibrationOffset(),
    val isMirrored: Boolean = false,
    val invertEyeX: Boolean = false,
    val invertEyeY: Boolean = false,
    val syncEyes: Boolean = true,
    val sendMergedEyes: Boolean = false
)

data class EyeCalibrationOffset(
    val lookOutLeft: Float = 0f,
    val lookInLeft: Float = 0f,
    val lookInRight: Float = 0f,
    val lookOutRight: Float = 0f,
    val lookUpLeft: Float = 0f,
    val lookDownLeft: Float = 0f,
    val lookUpRight: Float = 0f,
    val lookDownRight: Float = 0f
) {
    val isCalibrated: Boolean get() = lookOutLeft != 0f || lookInLeft != 0f ||
        lookInRight != 0f || lookOutRight != 0f ||
        lookUpLeft != 0f || lookDownLeft != 0f ||
        lookUpRight != 0f || lookDownRight != 0f
}

data class MouthCalibrationOffset(
    val closedJawOpen: Float = 0f,
    val closedMouthClose: Float = 0f,
    val maxJawOpen: Float = 1f,
    val maxMouthClose: Float = 0f
) {
    val isCalibrated: Boolean get() = closedJawOpen != 0f || maxJawOpen != 1f
}

/**
 * 模型文件加载辅助
 */
object ModelHelper {
    private const val TAG = "ModelHelper"
    private const val MODEL_NAME = "face_landmarker.task"
    private const val MODEL_DIR = "models"

    @Throws(Exception::class)
    fun loadModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, MODEL_DIR)
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val modelFile = File(modelsDir, MODEL_NAME)

        if (modelFile.exists() && modelFile.length() > 0) {
            Log.d(TAG, "Model file exists: ${modelFile.absolutePath}")
            return modelFile
        }

        Log.d(TAG, "Copying model from assets...")
        context.assets.open(MODEL_NAME).use { inputStream ->
            FileOutputStream(modelFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }

        Log.d(TAG, "Model copied: ${modelFile.length()} bytes")
        return modelFile
    }
}

/**
 * MediaPipe 数据提取辅助
 *
 * 将 MediaPipe FaceLandmarker 的结果转换为 VRCFT Unified Expressions v2 参数。
 * 与 PC 版 face_landmark.py 的 MEDIAPIPE_TO_VRCFT 映射保持一致。
 */
object MediaPipeHelper {
    private const val TAG = "MediaPipeHelper"

    /**
     * 从 MediaPipe 结果提取 VRCFT 面部参数
     *
     * @param landmarks 面部关键点列表 (478个点, 每个有 x/y/z)
     * @param blendshapes 面部 blendshapes (52个)
     * @return VRCFT v2 参数 Map
     */
    fun extractFaceData(
        landmarks: List<NormalizedLandmark>,
        blendshapes: List<Category>,
        eyeSensitivity: Float = 1.0f,
        eyeCalibration: EyeCalibrationOffset = EyeCalibrationOffset(),
        mouthSensitivity: Float = 1.0f,
        mouthCalibration: MouthCalibrationOffset = MouthCalibrationOffset(),
        isMirrored: Boolean = false,
        invertEyeX: Boolean = false,
        invertEyeY: Boolean = false,
        syncEyes: Boolean = false,
        sendMergedEyes: Boolean = false
    ): Map<String, Float> {
        val result = mutableMapOf<String, Float>()

        val bsMap = mutableMapOf<String, Float>()
        for (category in blendshapes) {
            bsMap[category.categoryName()] = category.score()
        }

        // ===== 镜像翻转: 交换左右 blendshape =====
        val bs = if (isMirrored) {
            val mirrored = bsMap.toMutableMap()
            val swapPairs = listOf(
                "eyeBlinkLeft" to "eyeBlinkRight",
                "eyeWideLeft" to "eyeWideRight",
                "eyeSquintLeft" to "eyeSquintRight",
                "eyeLookOutLeft" to "eyeLookInRight",
                "eyeLookInLeft" to "eyeLookOutRight",
                "eyeLookUpLeft" to "eyeLookUpRight",
                "eyeLookDownLeft" to "eyeLookDownRight",
                "browDownLeft" to "browDownRight",
                "browOuterUpLeft" to "browOuterUpRight",
                "noseSneerLeft" to "noseSneerRight",
                "cheekSquintLeft" to "cheekSquintRight",
                "mouthSmileLeft" to "mouthSmileRight",
                "mouthFrownLeft" to "mouthFrownRight",
                "mouthDimpleLeft" to "mouthDimpleRight",
                "mouthPressLeft" to "mouthPressRight",
                "mouthStretchLeft" to "mouthStretchRight",
                "mouthLowerDownLeft" to "mouthLowerDownRight",
                "mouthUpperUpLeft" to "mouthUpperUpRight",
                "jawLeft" to "jawRight",
                "mouthLeft" to "mouthRight"
            )
            for ((l, r) in swapPairs) {
                val lv = bsMap[l] ?: 0f
                val rv = bsMap[r] ?: 0f
                mirrored[l] = rv
                mirrored[r] = lv
            }
            mirrored
        } else {
            bsMap
        }

        // ===== 嘴部 (带校准和灵敏度) =====
        val rawJawOpen = bs["jawOpen"] ?: 0f
        val rawMouthClose = bs["mouthClose"] ?: 0f

        val calJawOpen = if (mouthCalibration.isCalibrated) {
            val range = mouthCalibration.maxJawOpen - mouthCalibration.closedJawOpen
            if (range > 0.001f) ((rawJawOpen - mouthCalibration.closedJawOpen) / range) else rawJawOpen
        } else rawJawOpen

        val calMouthClose = if (mouthCalibration.isCalibrated) {
            val range = mouthCalibration.maxMouthClose - mouthCalibration.closedMouthClose
            if (range > 0.001f) ((rawMouthClose - mouthCalibration.closedMouthClose) / range) else rawMouthClose
        } else rawMouthClose

        result["v2/JawOpen"] = (calJawOpen * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthClosed"] = (calMouthClose * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/LipFunnel"] = ((bs["mouthFunnel"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/LipPucker"] = ((bs["mouthPucker"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/JawX"] = ((bs["jawRight"] ?: 0f) - (bs["jawLeft"] ?: 0f)) * mouthSensitivity
        result["v2/MouthX"] = ((bs["mouthRight"] ?: 0f) - (bs["mouthLeft"] ?: 0f)) * mouthSensitivity
        result["v2/MouthSmileLeft"] = ((bs["mouthSmileLeft"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthSmileRight"] = ((bs["mouthSmileRight"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthFrownLeft"] = ((bs["mouthFrownLeft"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthFrownRight"] = ((bs["mouthFrownRight"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthDimpleLeft"] = ((bs["mouthDimpleLeft"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthDimpleRight"] = ((bs["mouthDimpleRight"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthPressLeft"] = ((bs["mouthPressLeft"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthPressRight"] = ((bs["mouthPressRight"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthStretchLeft"] = ((bs["mouthStretchLeft"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthStretchRight"] = ((bs["mouthStretchRight"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthLowerDownLeft"] = ((bs["mouthLowerDownLeft"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthLowerDownRight"] = ((bs["mouthLowerDownRight"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthUpperUpLeft"] = ((bs["mouthUpperUpLeft"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthUpperUpRight"] = ((bs["mouthUpperUpRight"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthRaiserLower"] = ((bs["mouthRollLower"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)
        result["v2/MouthRaiserUpper"] = ((bs["mouthRollUpper"] ?: 0f) * mouthSensitivity).coerceIn(0f, 1f)

        // ===== 眼睛 (VRCFT 标准: 0=闭合, 0.75=正常, 1.0=睁大) =====
        for (side in listOf("Left", "Right")) {
            val blink = (bs["eyeBlink${side}"] ?: 0f) * eyeSensitivity
            val widen = (bs["eyeWide${side}"] ?: 0f) * eyeSensitivity
            val openness = 1.0f - blink.coerceIn(0f, 1f)
            val eyeLid = (openness * 0.75f + widen.coerceIn(0f, 1f) * 0.25f).coerceIn(0f, 1f)
            result["v2/EyeLid${side}"] = eyeLid
        }
        result["v2/EyeSquintLeft"] = (bs["eyeSquintLeft"] ?: 0f) * eyeSensitivity
        result["v2/EyeSquintRight"] = (bs["eyeSquintRight"] ?: 0f) * eyeSensitivity

        // ===== 眼睛视线 (校准后以校准位置为居中, 使用镜像后的 bs) =====
        val rawLookOutLeft = bs["eyeLookOutLeft"] ?: 0f
        val rawLookInLeft = bs["eyeLookInLeft"] ?: 0f
        val rawLookInRight = bs["eyeLookInRight"] ?: 0f
        val rawLookOutRight = bs["eyeLookOutRight"] ?: 0f
        val rawLookUpLeft = bs["eyeLookUpLeft"] ?: 0f
        val rawLookDownLeft = bs["eyeLookDownLeft"] ?: 0f
        val rawLookUpRight = bs["eyeLookUpRight"] ?: 0f
        val rawLookDownRight = bs["eyeLookDownRight"] ?: 0f

        val calLookOutLeft = rawLookOutLeft - eyeCalibration.lookOutLeft
        val calLookInLeft = rawLookInLeft - eyeCalibration.lookInLeft
        val calLookInRight = rawLookInRight - eyeCalibration.lookInRight
        val calLookOutRight = rawLookOutRight - eyeCalibration.lookOutRight
        val calLookUpLeft = rawLookUpLeft - eyeCalibration.lookUpLeft
        val calLookDownLeft = rawLookDownLeft - eyeCalibration.lookDownLeft
        val calLookUpRight = rawLookUpRight - eyeCalibration.lookUpRight
        val calLookDownRight = rawLookDownRight - eyeCalibration.lookDownRight

        // VRCFT 标准: EyeLeftX 正=右看(内看), 负=左看(外看)
        result["v2/EyeLeftX"] = ((calLookInLeft - calLookOutLeft) * eyeSensitivity).coerceIn(-1f, 1f)
        // VRCFT 标准: EyeRightX 正=右看(外看), 负=左看(内看)
        result["v2/EyeRightX"] = ((calLookOutRight - calLookInRight) * eyeSensitivity).coerceIn(-1f, 1f)
        result["v2/EyeLeftY"] = ((calLookUpLeft - calLookDownLeft) * eyeSensitivity).coerceIn(-1f, 1f)
        result["v2/EyeRightY"] = ((calLookUpRight - calLookDownRight) * eyeSensitivity).coerceIn(-1f, 1f)

        // ===== 眼部取负 (解决视线方向相反问题) =====
        if (invertEyeX) {
            result["v2/EyeLeftX"] = -(result["v2/EyeLeftX"]!!)
            result["v2/EyeRightX"] = -(result["v2/EyeRightX"]!!)
        }
        if (invertEyeY) {
            result["v2/EyeLeftY"] = -(result["v2/EyeLeftY"]!!)
            result["v2/EyeRightY"] = -(result["v2/EyeRightY"]!!)
        }

        // ===== 双眼同步 (右眼复制左眼数据，解决模型仅左眼运动问题) =====
        if (syncEyes) {
            result["v2/EyeRightX"] = result["v2/EyeLeftX"]!!
            result["v2/EyeRightY"] = result["v2/EyeLeftY"]!!
            result["v2/EyeLidRight"] = result["v2/EyeLidLeft"]!!
            result["v2/EyeSquintRight"] = result["v2/EyeSquintLeft"]!!
        }

        // ===== 合并眼部视线 (部分模型会用合并值覆盖右眼，默认不发送) =====
        if (sendMergedEyes) {
            result["v2/EyesX"] = ((result["v2/EyeLeftX"]!! + result["v2/EyeRightX"]!!) / 2f).coerceIn(-1f, 1f)
            result["v2/EyesY"] = ((result["v2/EyeLeftY"]!! + result["v2/EyeRightY"]!!) / 2f).coerceIn(-1f, 1f)
        }

        // ===== 眉毛 =====
        result["v2/BrowLowererLeft"] = bs["browDownLeft"] ?: 0f
        result["v2/BrowLowererRight"] = bs["browDownRight"] ?: 0f
        result["v2/BrowOuterUpLeft"] = bs["browOuterUpLeft"] ?: 0f
        result["v2/BrowOuterUpRight"] = bs["browOuterUpRight"] ?: 0f
        val browInner = bs["browInnerUp"] ?: 0f
        result["v2/BrowInnerUpLeft"] = browInner
        result["v2/BrowInnerUpRight"] = browInner

        // ===== 鼻子 =====
        result["v2/NoseSneerLeft"] = bs["noseSneerLeft"] ?: 0f
        result["v2/NoseSneerRight"] = bs["noseSneerRight"] ?: 0f

        // ===== 脸颊 =====
        result["v2/CheekSquintLeft"] = bs["cheekSquintLeft"] ?: 0f
        result["v2/CheekSquintRight"] = bs["cheekSquintRight"] ?: 0f
        result["v2/CheekPuffSuck"] = bs["cheekPuff"] ?: 0f

        // ===== 舌头 =====
        result["v2/TongueOut"] = bs["tongueOut"] ?: 0f

        // ===== 合并参数 =====
        result["v2/MouthOpen"] = (
            (result["v2/MouthUpperUpLeft"] ?: 0f) + (result["v2/MouthUpperUpRight"] ?: 0f)
        ) / 2f + (
            (result["v2/MouthLowerDownLeft"] ?: 0f) + (result["v2/MouthLowerDownRight"] ?: 0f)
        ) / 2f

        // ===== 追踪状态 =====
        result["ExpressionTrackingActive"] = 1.0f
        result["LipTrackingActive"] = 1.0f

        return result
    }
}
