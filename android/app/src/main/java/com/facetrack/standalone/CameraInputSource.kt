package com.facetrack.standalone

import java.io.Serializable

enum class CameraInputRole(val label: String) {
    LEFT_EYE("左眼"),
    RIGHT_EYE("右眼"),
    MOUTH("嘴部")
}

object CameraInputSources {
    const val AUTO = "auto"
    const val CAMERA2_EXTERNAL = "camera2"
    const val UVC = "uvc"
    const val AUTO_KEY = ""

    fun bindingKey(sourceType: String, deviceId: String): String {
        return if (sourceType == AUTO || deviceId.isBlank()) AUTO_KEY else "$sourceType:$deviceId"
    }

    fun sourceTypeFromKey(bindingKey: String): String {
        return bindingKey.substringBefore(':', AUTO)
    }

    fun deviceIdFromKey(bindingKey: String): String {
        return bindingKey.substringAfter(':', "")
    }
}

data class CameraInputDeviceOption(
    val sourceType: String,
    val deviceId: String,
    val displayName: String
) : Serializable {
    val bindingKey: String get() = CameraInputSources.bindingKey(sourceType, deviceId)
}

data class CameraInputBindingConfig(
    val leftEyeKey: String = CameraInputSources.AUTO_KEY,
    val rightEyeKey: String = CameraInputSources.AUTO_KEY,
    val mouthKey: String = CameraInputSources.AUTO_KEY
) : Serializable {
    fun keyFor(role: CameraInputRole): String = when (role) {
        CameraInputRole.LEFT_EYE -> leftEyeKey
        CameraInputRole.RIGHT_EYE -> rightEyeKey
        CameraInputRole.MOUTH -> mouthKey
    }

    fun withRole(role: CameraInputRole, key: String): CameraInputBindingConfig {
        return when (role) {
            CameraInputRole.LEFT_EYE -> copy(leftEyeKey = key)
            CameraInputRole.RIGHT_EYE -> copy(rightEyeKey = key)
            CameraInputRole.MOUTH -> copy(mouthKey = key)
        }
    }

    fun selectedKeys(): List<String> {
        return listOf(leftEyeKey, rightEyeKey, mouthKey).filter { it.isNotBlank() }
    }
}

data class CameraInputStatus(
    val role: CameraInputRole,
    val deviceName: String = "未连接",
    val sourceType: String = "UVC",
    val connected: Boolean = false,
    val permissionGranted: Boolean = false,
    val previewing: Boolean = false,
    val fps: Float = 0f,
    val width: Int = 0,
    val height: Int = 0,
    val droppedFrames: Long = 0L,
    val camera2Id: String = ""
) : Serializable

interface CameraInputSource {
    val role: CameraInputRole
    fun status(): CameraInputStatus
    fun stop()
    fun release()
}
