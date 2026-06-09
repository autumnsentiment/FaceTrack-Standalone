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
 *
 * VRCFT 参数地址映射:
 * - 默认发送 direct /avatar/parameters/... 地址
 * - 支持 VRCFT 别名映射 (处理参数名差异)
 * - 支持从 PC 端加载 Binary 编码参数 (BCD 编码)
 */
class FaceVMCStreamer(
    private val host: String,
    private val oscPort: Int,
    private val vmcPort: Int
) {

    companion object {
        private const val TAG = "FaceVMCStreamer"
        private const val BUFFER_SIZE = 4096

        private val VRCFT_ALIASES = mapOf(
            "v2/MouthSmileLeft" to "v2/SmileFrownLeft",
            "v2/MouthSmileRight" to "v2/SmileFrownRight",
            "v2/MouthFrownLeft" to "v2/SmileFrownLeft",
            "v2/MouthFrownRight" to "v2/SmileFrownRight",
            "v2/MouthLowerDownLeft" to "v2/MouthLowerDown",
            "v2/MouthLowerDownRight" to "v2/MouthLowerDown",
            "v2/MouthUpperUpLeft" to "v2/MouthUpperUp",
            "v2/MouthUpperUpRight" to "v2/MouthUpperUp",
            "v2/LipFunnel" to "v2/LipFunnelUpper",
            "v2/LipPucker" to "v2/LipPuckerUpper",
            "v2/MouthX" to "v2/MouthUpperX",
            "v2/CheekPuffSuck" to "v2/CheekPuffSuckRight",
            "v2/BrowLowererLeft" to "v2/BrowDownLeft",
            "v2/BrowLowererRight" to "v2/BrowDownRight",
            "v2/MouthRaiserLower" to "v2/MouthLowerDown",
            "v2/MouthRaiserUpper" to "v2/MouthUpperUp"
        )

        private val VRCFT_TO_VMC_BLENDSHAPE = mapOf(
            "v2/JawOpen" to "jaw_open",
            "v2/MouthClosed" to "mouth_close",
            "v2/LipFunnel" to "mouth_funnel",
            "v2/LipPucker" to "mouth_pucker",
            "v2/MouthSmileLeft" to "mouth_smile_left",
            "v2/MouthSmileRight" to "mouth_smile_right",
            "v2/MouthOpen" to "mouth_open",
            "v2/MouthSmile" to "mouth_smile",
            "v2/EyeLidLeft" to "eye_lid_left",
            "v2/EyeLidRight" to "eye_lid_right",
            "v2/EyeSquintLeft" to "eye_squint_left",
            "v2/EyeSquintRight" to "eye_squint_right",
            "v2/EyeLeftX" to "eye_look_left_x",
            "v2/EyeLeftY" to "eye_look_left_y",
            "v2/EyeRightX" to "eye_look_right_x",
            "v2/EyeRightY" to "eye_look_right_y",
            "v2/EyesX" to "eye_look_x",
            "v2/EyesY" to "eye_look_y",
            "v2/BrowOuterUpLeft" to "brow_outer_up_left",
            "v2/BrowOuterUpRight" to "brow_outer_up_right",
            "v2/BrowLowererLeft" to "brow_lowerer_left",
            "v2/BrowLowererRight" to "brow_lowerer_right",
            "v2/BrowInnerUpLeft" to "brow_inner_up_left",
            "v2/BrowInnerUpRight" to "brow_inner_up_right",
            "v2/NoseSneerLeft" to "nose_sneer_left",
            "v2/NoseSneerRight" to "nose_sneer_right",
            "v2/CheekSquintLeft" to "cheek_squint_left",
            "v2/CheekSquintRight" to "cheek_squint_right",
            "v2/CheekPuffSuck" to "cheek_puff",
            "v2/TongueOut" to "tongue_out"
        )

    }

    private var oscSocket: DatagramSocket? = null
    private var vmcSocket: DatagramSocket? = null
    private var inetAddress: InetAddress? = null
    private var isRunning = false

    private var vrcftParamMap: Map<String, String> = emptyMap()
    private var binaryParamMap: Map<String, Map<Int, String>> = emptyMap()
    private var useCustomMapping = false

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

    fun updateParamMap(floatMap: Map<String, String>, binaryMap: Map<String, Map<Int, String>>) {
        vrcftParamMap = floatMap
        binaryParamMap = binaryMap
        useCustomMapping = floatMap.isNotEmpty()
        Log.d(TAG, "Param map updated: ${floatMap.size} float, ${binaryMap.size} binary groups, custom=$useCustomMapping")
    }

    fun sendFaceData(faceData: Map<String, Float>) {
        if (!isRunning || inetAddress == null) return

        sendOSCFaceParams(faceData)
        sendVMCFace(faceData)
    }

    private fun resolveOscAddress(vrcftName: String): List<String> {
        val addresses = mutableListOf<String>()

        if (useCustomMapping && vrcftParamMap.isNotEmpty()) {
            val direct = vrcftParamMap[vrcftName]
            if (direct != null) {
                addresses.add(direct)
            }
            val aliasName = VRCFT_ALIASES[vrcftName]
            if (aliasName != null && aliasName != vrcftName) {
                val aliasAddr = vrcftParamMap[aliasName]
                if (aliasAddr != null && aliasAddr !in addresses) {
                    addresses.add(aliasAddr)
                }
            }
        }

        if (addresses.isEmpty()) {
            // 直接发送到 VRChat 参数地址 (不使用 ft/f/ 二进制编码前缀)
            addresses.add("/avatar/parameters/$vrcftName")
        }

        val directEyeParams = setOf(
            "v2/EyesX", "v2/EyesY",
            "v2/EyeLeftX", "v2/EyeLeftY", "v2/EyeRightX", "v2/EyeRightY"
        )
        if (vrcftName in directEyeParams) {
            // 添加不带 v2/ 前缀的地址 (VRChat 常用格式)
            val shortAddr = "/avatar/parameters/${vrcftName.removePrefix("v2/")}"
            if (shortAddr !in addresses) {
                addresses.add(shortAddr)
            }

            // VRCFT v4 兼容参数名 (EyesX/EyesY 对应 EyeLookLeftRight/EyeLookUpDown)
            val eyeAliases = mapOf(
                "v2/EyesX" to listOf("/avatar/parameters/EyeLookLeftRight", "/avatar/parameters/LookX"),
                "v2/EyesY" to listOf("/avatar/parameters/EyeLookUpDown", "/avatar/parameters/LookY"),
                "v2/EyeLeftX" to listOf("/avatar/parameters/LeftEyeX"),
                "v2/EyeLeftY" to listOf("/avatar/parameters/LeftEyeY"),
                "v2/EyeRightX" to listOf("/avatar/parameters/RightEyeX"),
                "v2/EyeRightY" to listOf("/avatar/parameters/RightEyeY")
            )
            eyeAliases[vrcftName]?.forEach { alias ->
                if (alias !in addresses) {
                    addresses.add(alias)
                }
            }
        }

        return addresses
    }

    private fun resolveBinaryAddresses(vrcftName: String, value: Float): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()

        if (!useCustomMapping || binaryParamMap.isEmpty()) {
            return result
        }

        var found = binaryParamMap[vrcftName]
        if (found == null) {
            val aliasName = VRCFT_ALIASES[vrcftName]
            if (aliasName != null) {
                found = binaryParamMap[aliasName]
            }
        }
        if (found == null) {
            for ((groupKey, groupBits) in binaryParamMap) {
                if (groupKey.endsWith("/$vrcftName")) {
                    found = groupBits
                    break
                }
            }
        }

        val bits = found ?: return result

        val absVal: Float
        if (bits.containsKey(-1)) {
            val isNegative = value < 0
            result.add(Pair(bits[-1]!!, if (isNegative) 1 else 0))
            absVal = kotlin.math.abs(value)
        } else {
            absVal = kotlin.math.max(0f, value)
        }

        val maxBits = bits.keys.filter { it > 0 }.sum()
        if (maxBits == 0) return result

        val scaled = (absVal * maxBits).toInt()

        for ((bitVal, addr) in bits) {
            if (bitVal == -1) continue
            val isSet = (scaled and bitVal) != 0
            result.add(Pair(addr, if (isSet) 1 else 0))
        }

        return result
    }

    private fun sendOSCFaceParams(faceData: Map<String, Float>) {
        val socket = oscSocket ?: return
        val addr = inetAddress ?: return

        val messages = mutableListOf<ByteArray>()

        for ((paramName, value) in faceData) {
            if (!paramName.startsWith("v2/")) continue

            val clampedValue = value.coerceIn(-1f, 1f)

            val oscAddresses = resolveOscAddress(paramName)
            for (oscAddr in oscAddresses) {
                try {
                    messages.add(buildOSCFLOATMessage(oscAddr, clampedValue))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to build OSC message for $oscAddr", e)
                }
            }

            val binaryAddrs = resolveBinaryAddresses(paramName, clampedValue)
            for ((binAddr, boolVal) in binaryAddrs) {
                try {
                    messages.add(buildOSCINTMessage(binAddr, boolVal))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to build binary OSC message for $binAddr", e)
                }
            }
        }

        for (msg in messages) {
            try {
                socket.send(DatagramPacket(msg, msg.size, addr, oscPort))
            } catch (e: IOException) {
                // 静默忽略单次发送失败
            }
        }
    }

    private fun sendVMCFace(faceData: Map<String, Float>) {
        val socket = vmcSocket ?: return
        val addr = inetAddress ?: return

        try {
            for ((paramName, value) in faceData) {
                val vmcName = vrcftToVMCBlendshape(paramName) ?: continue
                val msg = buildVMCBlendMessage(vmcName, value.coerceIn(-1f, 1f))
                socket.send(DatagramPacket(msg, msg.size, addr, vmcPort))
            }

            val applyMsg = buildOSCNOPARMMessage("/VMC/Ext/Blend/Apply")
            socket.send(DatagramPacket(applyMsg, applyMsg.size, addr, vmcPort))

            val okMsg = buildOSCINTMessage("/VMC/Ext/OK", 1)
            socket.send(DatagramPacket(okMsg, okMsg.size, addr, vmcPort))

        } catch (e: IOException) {
            Log.e(TAG, "VMC send error", e)
        }
    }

    private fun vrcftToVMCBlendshape(vrcftName: String): String? =
        VRCFT_TO_VMC_BLENDSHAPE[vrcftName]

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
