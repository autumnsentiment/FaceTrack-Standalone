package com.facetrack.standalone

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.CameraViewInterface
import com.serenegiant.widget.UVCCameraTextureView

class MultiUvcCameraController(
    private val context: Context,
    private val statusChanged: () -> Unit
) {
    companion object {
        private const val TAG = "MultiUvcCamera"
        private const val MAX_INPUTS = 3
    }

    private val roles = CameraInputRole.values().take(MAX_INPUTS)
    private val slots = roles.map { UvcSlot(it) }
    private var bindings = CameraInputBindingConfig()

    fun setBindings(newBindings: CameraInputBindingConfig) {
        bindings = newBindings
        refreshDevices()
    }

    fun deviceOptions(): List<CameraInputDeviceOption> {
        slots.forEach { it.ensureHelper() }
        return availableDevices().map { device ->
            CameraInputDeviceOption(
                sourceType = CameraInputSources.UVC,
                deviceId = uvcDeviceKey(device),
                displayName = "UVC ${device.deviceName}"
            )
        }
    }

    fun start() {
        slots.forEach { it.ensureHelper() }
        refreshDevices()
    }

    fun stop() {
        slots.forEach { it.stop() }
        statusChanged()
    }

    fun release() {
        slots.forEach { it.release() }
    }

    fun attachPreview(role: CameraInputRole, view: UVCCameraTextureView?) {
        slots.firstOrNull { it.role == role }?.setPreviewView(view)
    }

    fun status(): List<CameraInputStatus> {
        return slots.map { it.status() }
    }

    private fun availableDevices(): List<UsbDevice> {
        return slots.firstOrNull()?.helper?.deviceList.orEmpty()
            .filterIsInstance<UsbDevice>()
            .take(MAX_INPUTS)
    }

    private fun refreshDevices() {
        val devices = availableDevices()
        val usedDeviceKeys = mutableSetOf<String>()
        slots.forEachIndexed { index, slot ->
            val configuredKey = bindings.keyFor(slot.role)
            val configuredDevice = if (CameraInputSources.sourceTypeFromKey(configuredKey) == CameraInputSources.UVC) {
                val deviceId = CameraInputSources.deviceIdFromKey(configuredKey)
                devices.firstOrNull { uvcDeviceKey(it) == deviceId }
            } else {
                null
            }
            val device = configuredDevice ?: devices.firstOrNull { uvcDeviceKey(it) !in usedDeviceKeys }
                ?: devices.getOrNull(index)
            if (device != null) usedDeviceKeys.add(uvcDeviceKey(device))
            slot.bindDevice(device)
        }
        statusChanged()
    }

    private fun uvcDeviceKey(device: UsbDevice): String = device.deviceName

    private inner class UvcSlot(
        override val role: CameraInputRole
    ) : CameraInputSource {
        var helper: CameraHelper? = null
        private var device: UsbDevice? = null
        private var previewView: UVCCameraTextureView? = null
        private var permissionGranted = false
        private var connected = false
        private var previewing = false
        private var droppedFrames = 0L
        private var lastSize: Size? = null
        private var frameCount = 0L
        private var fpsTimestamp = System.nanoTime()
        private var currentFps = 0f

        override fun status(): CameraInputStatus {
            val size = lastSize ?: helper?.previewSize
            val fps = if (currentFps > 0f) currentFps else previewView?.fps ?: 0f
            return CameraInputStatus(
                role = role,
                deviceName = device?.deviceName ?: "未连接",
                sourceType = "UVC",
                connected = connected,
                permissionGranted = permissionGranted,
                previewing = previewing,
                fps = fps,
                width = size?.width ?: 0,
                height = size?.height ?: 0,
                droppedFrames = droppedFrames
            )
        }

        fun ensureHelper() {
            if (helper != null) return
            helper = CameraHelper().also { cameraHelper ->
                cameraHelper.setStateCallback(object : ICameraHelper.StateCallback {
                    override fun onAttach(device: UsbDevice) {
                        refreshDevices()
                    }

                    override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
                        if (this@UvcSlot.device == device) {
                            permissionGranted = true
                            connected = true
                            runCatching { cameraHelper.openCamera() }
                                .onFailure { error -> Log.w(TAG, "openCamera failed for ${role.label}", error) }
                        }
                        statusChanged()
                    }

                    override fun onCameraOpen(device: UsbDevice) {
                        if (this@UvcSlot.device == device) {
                            connected = true
                            permissionGranted = true
                            lastSize = cameraHelper.previewSize
                            runCatching {
                                cameraHelper.setFrameCallback(IFrameCallback { recordFrame() }, UVCCamera.PIXEL_FORMAT_YUV)
                            }.onFailure { error -> Log.w(TAG, "setFrameCallback failed for ${role.label}", error) }
                            attachSurfaceIfReady()
                            runCatching { cameraHelper.startPreview() }
                                .onSuccess { previewing = true }
                                .onFailure { error -> Log.w(TAG, "startPreview failed for ${role.label}", error) }
                        }
                        statusChanged()
                    }

                    override fun onCameraClose(device: UsbDevice) {
                        if (this@UvcSlot.device == device) {
                            previewing = false
                        }
                        statusChanged()
                    }

                    override fun onDeviceClose(device: UsbDevice) {
                        if (this@UvcSlot.device == device) {
                            connected = false
                            previewing = false
                        }
                        statusChanged()
                    }

                    override fun onDetach(device: UsbDevice) {
                        if (this@UvcSlot.device == device) {
                            this@UvcSlot.device = null
                            connected = false
                            permissionGranted = false
                            previewing = false
                        }
                        refreshDevices()
                    }

                    override fun onCancel(device: UsbDevice) {
                        if (this@UvcSlot.device == device) {
                            permissionGranted = false
                            previewing = false
                        }
                        statusChanged()
                    }

                    override fun onError(device: UsbDevice, error: CameraException) {
                        if (this@UvcSlot.device == device) {
                            droppedFrames++
                            previewing = false
                        }
                        Log.w(TAG, "UVC error for ${role.label}: ${error.message}")
                        statusChanged()
                    }
                })
                cameraHelper.registerCallback()
            }
        }

        fun bindDevice(newDevice: UsbDevice?) {
            ensureHelper()
            if (device == newDevice) return
            stop()
            device = newDevice
            connected = newDevice != null
            permissionGranted = false
            previewing = false
            if (newDevice != null) {
                runCatching { helper?.selectDevice(newDevice) }
                    .onFailure { error ->
                        droppedFrames++
                        Log.w(TAG, "selectDevice failed for ${role.label}", error)
                    }
            }
        }

        fun setPreviewView(view: UVCCameraTextureView?) {
            if (previewView == view) return
            removeSurface()
            previewView = view
            view?.setCallback(object : CameraViewInterface.Callback {
                override fun onSurfaceCreated(view: CameraViewInterface, surface: Surface) {
                    attachSurfaceIfReady()
                }

                override fun onSurfaceChanged(view: CameraViewInterface, surface: Surface, width: Int, height: Int) {
                    statusChanged()
                }

                override fun onSurfaceDestroy(view: CameraViewInterface, surface: Surface) {
                    removeSurface()
                }
            })
            attachSurfaceIfReady()
            statusChanged()
        }

        private fun attachSurfaceIfReady() {
            val surface = previewView?.surface ?: return
            val cameraHelper = helper ?: return
            if (!cameraHelper.isCameraOpened) return
            runCatching { cameraHelper.addSurface(surface, false) }
                .onFailure { error -> Log.w(TAG, "addSurface failed for ${role.label}", error) }
        }

        private fun removeSurface() {
            val surface = previewView?.surface ?: return
            runCatching { helper?.removeSurface(surface) }
        }

        private fun recordFrame() {
            frameCount++
            val now = System.nanoTime()
            val elapsed = (now - fpsTimestamp) / 1_000_000_000f
            if (elapsed >= 1f) {
                currentFps = frameCount / elapsed
                frameCount = 0
                fpsTimestamp = now
                statusChanged()
            }
        }

        override fun stop() {
            removeSurface()
            runCatching { helper?.stopPreview() }
            runCatching { helper?.closeCamera() }
            previewing = false
        }

        override fun release() {
            stop()
            runCatching { helper?.unregisterCallback() }
            runCatching { helper?.release() }
            helper = null
        }
    }
}
