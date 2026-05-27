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
    val faceTrackingConfidence: Float = 0.5f
)

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
        blendshapes: List<Category>
    ): Map<String, Float> {
        val result = mutableMapOf<String, Float>()

        // 构建 blendshape 映射
        val bsMap = mutableMapOf<String, Float>()
        for (category in blendshapes) {
            bsMap[category.categoryName()] = category.score()
        }

        // ===== 嘴部 =====
        result["v2/JawOpen"] = bsMap["jawOpen"] ?: 0f
        result["v2/MouthClosed"] = 1f - (bsMap["mouthClose"] ?: 0f)
        result["v2/LipFunnel"] = bsMap["mouthFunnel"] ?: 0f
        result["v2/LipPucker"] = bsMap["mouthPucker"] ?: 0f
        result["v2/JawX"] = (bsMap["jawRight"] ?: 0f) - (bsMap["jawLeft"] ?: 0f)
        result["v2/MouthX"] = (bsMap["mouthRight"] ?: 0f) - (bsMap["mouthLeft"] ?: 0f)
        result["v2/MouthSmileLeft"] = bsMap["mouthSmileLeft"] ?: 0f
        result["v2/MouthSmileRight"] = bsMap["mouthSmileRight"] ?: 0f
        result["v2/MouthFrownLeft"] = bsMap["mouthFrownLeft"] ?: 0f
        result["v2/MouthFrownRight"] = bsMap["mouthFrownRight"] ?: 0f
        result["v2/MouthDimpleLeft"] = bsMap["mouthDimpleLeft"] ?: 0f
        result["v2/MouthDimpleRight"] = bsMap["mouthDimpleRight"] ?: 0f
        result["v2/MouthPressLeft"] = bsMap["mouthPressLeft"] ?: 0f
        result["v2/MouthPressRight"] = bsMap["mouthPressRight"] ?: 0f
        result["v2/MouthStretchLeft"] = bsMap["mouthStretchLeft"] ?: 0f
        result["v2/MouthStretchRight"] = bsMap["mouthStretchRight"] ?: 0f
        result["v2/MouthLowerDownLeft"] = bsMap["mouthLowerDownLeft"] ?: 0f
        result["v2/MouthLowerDownRight"] = bsMap["mouthLowerDownRight"] ?: 0f
        result["v2/MouthUpperUpLeft"] = bsMap["mouthUpperUpLeft"] ?: 0f
        result["v2/MouthUpperUpRight"] = bsMap["mouthUpperUpRight"] ?: 0f
        result["v2/MouthRaiserLower"] = bsMap["mouthRollLower"] ?: 0f
        result["v2/MouthRaiserUpper"] = bsMap["mouthRollUpper"] ?: 0f

        // ===== 眼睛 (VRCFT 标准: 0=闭合, 0.75=正常, 1.0=睁大) =====
        for (side in listOf("Left", "Right")) {
            val blink = bsMap["eyeBlink${side}"] ?: 0f
            val widen = bsMap["eyeWide${side}"] ?: 0f
            val openness = 1.0f - blink
            val eyeLid = (openness * 0.75f + widen * 0.25f).coerceIn(0f, 1f)
            result["v2/EyeLid${side}"] = eyeLid
        }
        result["v2/EyeSquintLeft"] = bsMap["eyeSquintLeft"] ?: 0f
        result["v2/EyeSquintRight"] = bsMap["eyeSquintRight"] ?: 0f

        // ===== 眼睛视线 =====
        result["v2/EyeLeftX"] = ((bsMap["eyeLookOutLeft"] ?: 0f) - (bsMap["eyeLookInLeft"] ?: 0f)).coerceIn(-1f, 1f)
        result["v2/EyeRightX"] = ((bsMap["eyeLookInRight"] ?: 0f) - (bsMap["eyeLookOutRight"] ?: 0f)).coerceIn(-1f, 1f)
        result["v2/EyeLeftY"] = ((bsMap["eyeLookUpLeft"] ?: 0f) - (bsMap["eyeLookDownLeft"] ?: 0f)).coerceIn(-1f, 1f)
        result["v2/EyeRightY"] = ((bsMap["eyeLookUpRight"] ?: 0f) - (bsMap["eyeLookDownRight"] ?: 0f)).coerceIn(-1f, 1f)

        // ===== 眉毛 =====
        result["v2/BrowLowererLeft"] = bsMap["browDownLeft"] ?: 0f
        result["v2/BrowLowererRight"] = bsMap["browDownRight"] ?: 0f
        result["v2/BrowOuterUpLeft"] = bsMap["browOuterUpLeft"] ?: 0f
        result["v2/BrowOuterUpRight"] = bsMap["browOuterUpRight"] ?: 0f
        // browInnerUp 同时映射到左右
        val browInner = bsMap["browInnerUp"] ?: 0f
        result["v2/BrowInnerUpLeft"] = browInner
        result["v2/BrowInnerUpRight"] = browInner

        // ===== 鼻子 =====
        result["v2/NoseSneerLeft"] = bsMap["noseSneerLeft"] ?: 0f
        result["v2/NoseSneerRight"] = bsMap["noseSneerRight"] ?: 0f

        // ===== 脸颊 =====
        result["v2/CheekSquintLeft"] = bsMap["cheekSquintLeft"] ?: 0f
        result["v2/CheekSquintRight"] = bsMap["cheekSquintRight"] ?: 0f
        result["v2/CheekPuffSuck"] = bsMap["cheekPuff"] ?: 0f

        // ===== 舌头 =====
        result["v2/TongueOut"] = bsMap["tongueOut"] ?: 0f

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
