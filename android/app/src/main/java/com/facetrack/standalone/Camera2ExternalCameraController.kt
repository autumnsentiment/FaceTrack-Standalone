package com.facetrack.standalone

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import com.serenegiant.widget.CameraViewInterface
import com.serenegiant.widget.UVCCameraTextureView
import kotlin.math.abs

class Camera2ExternalCameraController(
    context: Context,
    private val statusChanged: () -> Unit
) {
    companion object {
        private const val TAG = "Camera2External"
        private const val MAX_INPUTS = 3
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
        private const val MAX_VALIDATION_WIDTH = 1280
        private const val MAX_VALIDATION_HEIGHT = 720
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraThread = HandlerThread("Camera2ExternalValidation").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val roles = CameraInputRole.values().take(MAX_INPUTS)
    private val slots = roles.map { Camera2Slot(it) }

    fun setCameraIds(ids: List<String>, bindings: CameraInputBindingConfig = CameraInputBindingConfig()) {
        val selectedIds = ids.take(MAX_INPUTS)
        val usedIds = mutableSetOf<String>()
        slots.forEachIndexed { index, slot ->
            val configuredKey = bindings.keyFor(slot.role)
            val configuredId = if (CameraInputSources.sourceTypeFromKey(configuredKey) == CameraInputSources.CAMERA2_EXTERNAL) {
                CameraInputSources.deviceIdFromKey(configuredKey).takeIf { it in selectedIds }
            } else {
                null
            }
            val cameraId = configuredId ?: selectedIds.firstOrNull { it !in usedIds }
                ?: selectedIds.getOrNull(index)
            if (cameraId != null) usedIds.add(cameraId)
            slot.bindCameraId(cameraId)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        slots.forEach { it.start() }
    }

    fun stop() {
        slots.forEach { it.stop() }
        statusChanged()
    }

    fun release() {
        stop()
        cameraThread.quitSafely()
    }

    fun attachPreview(role: CameraInputRole, view: UVCCameraTextureView?) {
        slots.firstOrNull { it.role == role }?.setPreviewView(view)
    }

    fun status(): List<CameraInputStatus> = slots.map { it.status() }

    private fun chooseStream(cameraId: String): SelectedStream {
        val map = cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val yuvSizes = map.outputSizesFor(ImageFormat.YUV_420_888)
        if (yuvSizes.isNotEmpty()) {
            return SelectedStream(chooseSize(yuvSizes), ImageFormat.YUV_420_888)
        }

        val privateSizes = map.outputSizesFor(ImageFormat.PRIVATE)
        if (privateSizes.isNotEmpty()) {
            return SelectedStream(chooseSize(privateSizes), ImageFormat.PRIVATE)
        }

        val textureSizes = map.outputSizesFor(SurfaceTexture::class.java)
        return SelectedStream(
            chooseSize(textureSizes.ifEmpty { arrayOf(Size(TARGET_WIDTH, TARGET_HEIGHT)) }),
            ImageFormat.PRIVATE
        )
    }

    private fun StreamConfigurationMap?.outputSizesFor(format: Int): Array<Size> {
        return runCatching { this?.getOutputSizes(format) ?: emptyArray() }.getOrDefault(emptyArray())
    }

    private fun StreamConfigurationMap?.outputSizesFor(klass: Class<*>): Array<Size> {
        return runCatching { this?.getOutputSizes(klass) ?: emptyArray() }.getOrDefault(emptyArray())
    }

    private fun chooseSize(sizes: Array<Size>): Size {
        val targetArea = TARGET_WIDTH * TARGET_HEIGHT
        val bounded = sizes.filter {
            it.width <= MAX_VALIDATION_WIDTH && it.height <= MAX_VALIDATION_HEIGHT
        }.ifEmpty { sizes.toList() }
        return bounded.minByOrNull { size ->
            abs(size.width * size.height - targetArea) + abs(size.width - TARGET_WIDTH)
        } ?: Size(TARGET_WIDTH, TARGET_HEIGHT)
    }

    private data class SelectedStream(
        val size: Size,
        val format: Int
    )

    private inner class Camera2Slot(
        override val role: CameraInputRole
    ) : CameraInputSource {
        @Volatile
        private var currentStatus = CameraInputStatus(role, sourceType = "Camera2 external")
        @Volatile
        private var previewView: UVCCameraTextureView? = null

        private var cameraId: String? = null
        private var selectedStream: SelectedStream? = null
        private var imageReader: ImageReader? = null
        private var cameraDevice: CameraDevice? = null
        private var captureSession: CameraCaptureSession? = null
        private var frameCount = 0L
        private var fpsTimestamp = System.nanoTime()

        override fun status(): CameraInputStatus = currentStatus

        fun bindCameraId(newCameraId: String?) {
            cameraHandler.post {
                if (cameraId == newCameraId) return@post
                closeCamera()
                cameraId = newCameraId
                if (newCameraId == null) {
                    selectedStream = null
                    currentStatus = CameraInputStatus(role, sourceType = "Camera2 external")
                    statusChanged()
                    return@post
                }

                val stream = runCatching { chooseStream(newCameraId) }
                    .onFailure { error -> Log.w(TAG, "Failed to choose stream for $newCameraId", error) }
                    .getOrElse { SelectedStream(Size(TARGET_WIDTH, TARGET_HEIGHT), ImageFormat.PRIVATE) }
                selectedStream = stream
                createImageReader(stream)
                currentStatus = CameraInputStatus(
                    role = role,
                    deviceName = "Camera2 external $newCameraId",
                    sourceType = "Camera2 external",
                    connected = true,
                    permissionGranted = true,
                    width = stream.size.width,
                    height = stream.size.height,
                    camera2Id = newCameraId
                )
                statusChanged()
            }
        }

        @SuppressLint("MissingPermission")
        fun start() {
            cameraHandler.post {
                val id = cameraId ?: return@post
                if (cameraDevice != null) {
                    configureSession()
                    return@post
                }
                try {
                    cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            currentStatus = currentStatus.copy(
                                connected = true,
                                permissionGranted = true,
                                previewing = false
                            )
                            configureSession()
                            statusChanged()
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            cameraDevice = null
                            currentStatus = currentStatus.copy(
                                connected = false,
                                previewing = false
                            )
                            statusChanged()
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                            cameraDevice = null
                            currentStatus = currentStatus.copy(
                                connected = false,
                                previewing = false,
                                droppedFrames = currentStatus.droppedFrames + 1
                            )
                            Log.w(TAG, "Camera2 external error for ${role.label}: $error")
                            statusChanged()
                        }
                    }, cameraHandler)
                } catch (security: SecurityException) {
                    currentStatus = currentStatus.copy(permissionGranted = false, previewing = false)
                    Log.w(TAG, "Missing camera permission for Camera2 external ${role.label}", security)
                    statusChanged()
                } catch (error: Exception) {
                    currentStatus = currentStatus.copy(
                        previewing = false,
                        droppedFrames = currentStatus.droppedFrames + 1
                    )
                    Log.w(TAG, "Open Camera2 external failed for ${role.label}", error)
                    statusChanged()
                }
            }
        }

        fun setPreviewView(view: UVCCameraTextureView?) {
            if (previewView == view) return
            previewView = view
            view?.setCallback(object : CameraViewInterface.Callback {
                override fun onSurfaceCreated(view: CameraViewInterface, surface: Surface) {
                    cameraHandler.post { configureSession() }
                }

                override fun onSurfaceChanged(view: CameraViewInterface, surface: Surface, width: Int, height: Int) {
                    cameraHandler.post { configureSession() }
                }

                override fun onSurfaceDestroy(view: CameraViewInterface, surface: Surface) {
                    cameraHandler.post { configureSession() }
                }
            })
            cameraHandler.post { configureSession() }
            statusChanged()
        }

        private fun createImageReader(stream: SelectedStream) {
            imageReader?.close()
            imageReader = ImageReader.newInstance(
                stream.size.width,
                stream.size.height,
                stream.format,
                2
            ).also { reader ->
                reader.setOnImageAvailableListener({ imageReader ->
                    try {
                        imageReader.acquireLatestImage()?.close()
                        recordFrame()
                    } catch (error: Exception) {
                        currentStatus = currentStatus.copy(
                            droppedFrames = currentStatus.droppedFrames + 1
                        )
                    }
                }, cameraHandler)
            }
        }

        private fun configureSession() {
            val device = cameraDevice ?: return
            val readerSurface = imageReader?.surface ?: return
            val targets = mutableListOf(readerSurface)
            previewSurface()?.let { targets.add(it) }

            closeSession()
            try {
                @Suppress("DEPRECATION")
                device.createCaptureSession(
                    targets,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            if (cameraDevice == null) {
                                session.close()
                                return
                            }
                            captureSession = session
                            startRepeating(session, targets)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            currentStatus = currentStatus.copy(
                                previewing = false,
                                droppedFrames = currentStatus.droppedFrames + 1
                            )
                            Log.w(TAG, "Configure Camera2 external failed for ${role.label}")
                            statusChanged()
                        }
                    },
                    cameraHandler
                )
            } catch (error: Exception) {
                currentStatus = currentStatus.copy(
                    previewing = false,
                    droppedFrames = currentStatus.droppedFrames + 1
                )
                Log.w(TAG, "Create Camera2 external session failed for ${role.label}", error)
                statusChanged()
            }
        }

        private fun previewSurface(): Surface? {
            val view = previewView ?: return null
            if (!view.hasSurface()) return null
            val size = selectedStream?.size ?: return null
            view.surfaceTexture?.setDefaultBufferSize(size.width, size.height)
            return view.surface?.takeIf { it.isValid }
        }

        private fun startRepeating(session: CameraCaptureSession, targets: List<Surface>) {
            try {
                val request = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
                targets.forEach { request.addTarget(it) }
                request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                session.setRepeatingRequest(request.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        currentStatus = currentStatus.copy(
                            droppedFrames = currentStatus.droppedFrames + 1
                        )
                    }
                }, cameraHandler)
                currentStatus = currentStatus.copy(previewing = true)
                statusChanged()
            } catch (error: Exception) {
                currentStatus = currentStatus.copy(
                    previewing = false,
                    droppedFrames = currentStatus.droppedFrames + 1
                )
                Log.w(TAG, "Start Camera2 external repeating failed for ${role.label}", error)
                statusChanged()
            }
        }

        private fun recordFrame() {
            frameCount++
            val now = System.nanoTime()
            val elapsed = (now - fpsTimestamp) / 1_000_000_000f
            if (elapsed >= 1f) {
                currentStatus = currentStatus.copy(fps = frameCount / elapsed)
                frameCount = 0
                fpsTimestamp = now
                statusChanged()
            }
        }

        private fun closeSession() {
            runCatching { captureSession?.stopRepeating() }
            runCatching { captureSession?.abortCaptures() }
            runCatching { captureSession?.close() }
            captureSession = null
        }

        private fun closeCamera() {
            closeSession()
            runCatching { cameraDevice?.close() }
            cameraDevice = null
            runCatching { imageReader?.close() }
            imageReader = null
            currentStatus = currentStatus.copy(previewing = false)
        }

        override fun stop() {
            cameraHandler.post {
                closeCamera()
                currentStatus = currentStatus.copy(previewing = false)
                statusChanged()
            }
        }

        override fun release() {
            stop()
        }
    }
}
