package com.facetrack.standalone

import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * 面部 VMC/OSC 推流器
 *
 * 同时发送两种协议:
 * 1. OSC (端口 9000): VRChat 面部参数 (VRCFT 标准)
 * 2. VMC (端口 39539): VMC 面部 blendshape
 */
class FaceVMCStreamer(
    private val host: String,
    private val oscPort: Int,
    private val vmcPort: Int
) {

    companion object {
        private const val TAG = "FaceVMCStreamer"
        private const val BUFFER_SIZE = 4096
    }

    private var oscSocket: DatagramSocket? = null
    private var vmcSocket: DatagramSocket? = null
    private var inetAddress: InetAddress? = null
    private var isRunning = false

    /**
     * 连接推流目标
     */
    fun connect(): Boolean {
        try {
            inetAddress = InetAddress.getByName(host)

            oscSocket = DatagramSocket()
            vmcSocket = DatagramSocket()

            isRunning = true
            Log.d(TAG, "Connected: OSC $host:$oscPort, VMC $host:$vmcPort")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to connect", e)
            disconnect()
            return false
        }
    }

    fun disconnect() {
        isRunning = false
        oscSocket?.close()
        vmcSocket?.close()
        oscSocket = null
        vmcSocket = null
        inetAddress = null
        Log.d(TAG, "Disconnected")
    }

    val isConnected: Boolean get() = isRunning

    /**
     * 发送面部数据
     */
    fun sendFaceData(faceData: Map<String, Float>) {
        if (!isRunning || inetAddress == null) return

        sendOSCFaceParams(faceData)
        sendVMCFace(faceData)
    }

    /**
     * OSC 面部参数 (VRCFT v2)
     */
    private fun sendOSCFaceParams(faceData: Map<String, Float>) {
        val socket = oscSocket ?: return
        val addr = inetAddress ?: return

        for ((paramName, value) in faceData) {
            if (!paramName.startsWith("v2/")) continue
            try {
                val oscAddress = "/avatar/parameters/$paramName"
                val message = buildOSCFLOATMessage(oscAddress, value.coerceIn(-1f, 1f))
                socket.send(DatagramPacket(message, message.size, addr, oscPort))
            } catch (e: IOException) {
                // 静默忽略单次发送失败
            }
        }
    }

    /**
     * VMC 面部 blendshape
     */
    private fun sendVMCFace(faceData: Map<String, Float>) {
        val socket = vmcSocket ?: return
        val addr = inetAddress ?: return

        try {
            for ((paramName, value) in faceData) {
                val vmcName = vrcftToVMCBlendshape(paramName) ?: continue
                val msg = buildVMCBlendMessage(vmcName, value.coerceIn(-1f, 1f))
                socket.send(DatagramPacket(msg, msg.size, addr, vmcPort))
            }

            // Apply
            val applyMsg = buildOSCNOPARMMessage("/VMC/Ext/Blend/Apply")
            socket.send(DatagramPacket(applyMsg, applyMsg.size, addr, vmcPort))

            // OK
            val okMsg = buildOSCINTMessage("/VMC/Ext/OK", 1)
            socket.send(DatagramPacket(okMsg, okMsg.size, addr, vmcPort))

        } catch (e: IOException) {
            Log.e(TAG, "VMC send error", e)
        }
    }

    private fun vrcftToVMCBlendshape(vrcftName: String): String? = when (vrcftName) {
        "v2/JawOpen" -> "jaw_open"
        "v2/MouthClosed" -> "mouth_close"
        "v2/LipFunnel" -> "mouth_funnel"
        "v2/LipPucker" -> "mouth_pucker"
        "v2/MouthSmileLeft" -> "mouth_smile_left"
        "v2/MouthSmileRight" -> "mouth_smile_right"
        "v2/EyeLidLeft" -> "eye_lid_left"
        "v2/EyeLidRight" -> "eye_lid_right"
        "v2/EyeSquintLeft" -> "eye_squint_left"
        "v2/EyeSquintRight" -> "eye_squint_right"
        "v2/BrowOuterUpLeft" -> "brow_outer_up_left"
        "v2/BrowOuterUpRight" -> "brow_outer_up_right"
        "v2/BrowLowererLeft" -> "brow_lowerer_left"
        "v2/BrowLowererRight" -> "brow_lowerer_right"
        "v2/NoseSneerLeft" -> "nose_sneer_left"
        "v2/NoseSneerRight" -> "nose_sneer_right"
        "v2/CheekSquintLeft" -> "cheek_squint_left"
        "v2/CheekSquintRight" -> "cheek_squint_right"
        "v2/CheekPuffSuck" -> "cheek_puff"
        "v2/TongueOut" -> "tongue_out"
        else -> null
    }

    // ========== OSC 消息编码 ==========

    private fun buildOSCFLOATMessage(address: String, value: Float): ByteArray {
        val addrBytes = pad4(address.toByteArray(Charsets.UTF_8))
        val tagBytes = pad4(",f".toByteArray(Charsets.UTF_8))
        val valBytes = floatToBytes(value)
        return addrBytes + tagBytes + valBytes
    }

    private fun buildOSCINTMessage(address: String, value: Int): ByteArray {
        val addrBytes = pad4(address.toByteArray(Charsets.UTF_8))
        val tagBytes = pad4(",i".toByteArray(Charsets.UTF_8))
        val valBytes = intToBytes(value)
        return addrBytes + tagBytes + valBytes
    }

    private fun buildOSCNOPARMMessage(address: String): ByteArray {
        val addrBytes = pad4(address.toByteArray(Charsets.UTF_8))
        val tagBytes = pad4(",".toByteArray(Charsets.UTF_8))
        return addrBytes + tagBytes
    }

    private fun buildVMCBlendMessage(name: String, value: Float): ByteArray {
        val addrBytes = pad4("/VMC/Ext/Blend/Val".toByteArray(Charsets.UTF_8))
        val tagBytes = pad4(",sf".toByteArray(Charsets.UTF_8))
        val nameBytes = pad4(name.toByteArray(Charsets.UTF_8))
        val valBytes = floatToBytes(value)
        return addrBytes + tagBytes + nameBytes + valBytes
    }

    private fun pad4(data: ByteArray): ByteArray {
        val padding = (4 - data.size % 4) % 4
        if (padding == 0) return data
        return data + ByteArray(padding)
    }

    private fun floatToBytes(value: Float): ByteArray = intToBytes(
        java.lang.Float.floatToRawIntBits(value)
    )

    private fun intToBytes(value: Int): ByteArray = ByteArray(4).apply {
        this[0] = (value shr 24).toByte()
        this[1] = (value shr 16).toByte()
        this[2] = (value shr 8).toByte()
        this[3] = value.toByte()
    }
}
