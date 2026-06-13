package com.facetrack.standalone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.serenegiant.widget.UVCCameraTextureView
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceTrackingService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "FaceTrackingService"
        private const val CHANNEL_ID = "facetrack_tracking"
        private const val NOTIFICATION_ID = 14

        const val ACTION_START_CAMERA = "com.facetrack.standalone.action.START_CAMERA"
        const val ACTION_STOP_CAMERA = "com.facetrack.standalone.action.STOP_CAMERA"
        const val ACTION_START_TRACKING = "com.facetrack.standalone.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.facetrack.standalone.action.STOP_TRACKING"
        const val ACTION_SWITCH_CAMERA = "com.facetrack.standalone.action.SWITCH_CAMERA"
        const val ACTION_UPDATE_CONFIG = "com.facetrack.standalone.action.UPDATE_CONFIG"
        const val ACTION_START_UVC_VALIDATION = "com.facetrack.standalone.action.START_UVC_VALIDATION"
        const val ACTION_STOP_UVC_VALIDATION = "com.facetrack.standalone.action.STOP_UVC_VALIDATION"
        const val ACTION_STATUS = "com.facetrack.standalone.action.STATUS"

        const val EXTRA_CONFIG = "config"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_CAMERA_STARTED = "cameraStarted"
        const val EXTRA_FACE_DETECTED = "faceDetected"
        const val EXTRA_FPS = "fps"
        const val EXTRA_STATUS = "status"
        const val EXTRA_RAW_FACE_DATA = "rawFaceData"
        const val EXTRA_STREAM_FACE_DATA = "streamFaceData"
        const val EXTRA_BLENDSHAPES = "blendshapes"
        const val EXTRA_FRONT_CAMERA = "frontCamera"
        const val EXTRA_UVC_RUNNING = "uvcRunning"
        const val EXTRA_UVC_STATUSES = "uvcStatuses"
        const val EXTRA_EXTERNAL_CAMERA_IDS = "externalCameraIds"
        const val EXTRA_CAMERA_INPUT_OPTIONS = "cameraInputOptions"

        @Volatile
        private var activeService: FaceTrackingService? = null

        fun attachPreview(previewView: PreviewView?) {
            activeService?.setPreviewView(previewView)
        }

        fun detachPreview(previewView: PreviewView?) {
            val service = activeService ?: return
            if (previewView == null || service.previewView == previewView) {
                service.setPreviewView(null)
            }
        }

        fun attachUvcPreview(role: CameraInputRole, previewView: UVCCameraTextureView?) {
            activeService?.setExternalValidationPreview(role, previewView)
        }

        fun detachUvcPreview(role: CameraInputRole) {
            activeService?.setExternalValidationPreview(role, null)
        }

        fun isActive(): Boolean = activeService != null
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var config = AppConfig()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var previewView: PreviewView? = null
    private lateinit var cameraExecutor: ExecutorService
    private var faceLandmarker: FaceLandmarker? = null
    private var faceStreamer: FaceVMCStreamer? = null
    private var uvcController: MultiUvcCameraController? = null
    private var camera2ExternalController: Camera2ExternalCameraController? = null
    private val externalPreviewViews = mutableMapOf<CameraInputRole, UVCCameraTextureView?>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var isCameraStarted = false
    private var isRunning = false
    private var isUvcValidationRunning = false
    private var isFrontCamera = true
    private var faceDetected = false
    private var frameCount = 0L
    private var fpsTimestamp = System.nanoTime()
    private var currentFps = 0f

    override fun onCreate() {
        super.onCreate()
        activeService = this
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("准备就绪"))
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        initFaceLandmarker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateConfigFromIntent(intent)
        when (intent?.action) {
            ACTION_START_CAMERA -> startCamera()
            ACTION_STOP_CAMERA -> stopCamera()
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
            ACTION_SWITCH_CAMERA -> switchCamera()
            ACTION_START_UVC_VALIDATION -> startUvcValidation()
            ACTION_STOP_UVC_VALIDATION -> stopUvcValidation()
            ACTION_UPDATE_CONFIG -> {
                reloadFaceLandmarker()
                rebindCameraIfNeeded()
                if (isUvcValidationRunning) {
                    startExternalCameraValidation()
                }
                broadcastStatus("配置已更新")
            }
            else -> broadcastStatus("服务已启动")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeService = null
        stopTracking()
        stopCamera()
        faceLandmarker?.close()
        faceLandmarker = null
        uvcController?.release()
        uvcController = null
        camera2ExternalController?.release()
        camera2ExternalController = null
        cameraExecutor.shutdown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun updateConfigFromIntent(intent: Intent?) {
        val incoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getSerializableExtra(EXTRA_CONFIG, AppConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getSerializableExtra(EXTRA_CONFIG) as? AppConfig
        }
        if (incoming != null) {
            config = incoming
        }
    }

    private fun initFaceLandmarker() {
        try {
            ModelHelper.loadModelFile(this)
            val backend = resolveConfiguredBackend()
            val delegate = if (backend == NativeHelper.AccelerationBackend.CPU) Delegate.CPU else Delegate.GPU
            faceLandmarker = createFaceLandmarker(delegate)
            broadcastStatus("模型已加载 (${delegate.name})")
        } catch (e: Exception) {
            Log.e(TAG, "GPU model loading failed, trying CPU fallback", e)
            try {
                faceLandmarker = createFaceLandmarker(Delegate.CPU)
                broadcastStatus("模型已加载 (CPU)")
            } catch (cpuError: Exception) {
                Log.e(TAG, "FaceLandmarker loading failed", cpuError)
                broadcastStatus("模型加载失败: ${cpuError.message}")
            }
        }
    }

    private fun reloadFaceLandmarker() {
        val old = faceLandmarker
        faceLandmarker = null
        old?.close()
        initFaceLandmarker()
    }

    private fun resolveConfiguredBackend(): NativeHelper.AccelerationBackend {
        return when (config.backend) {
            NativeHelper.AccelerationBackend.AUTO -> NativeHelper.resolveAutoBackend()
            NativeHelper.AccelerationBackend.NNAPI,
            NativeHelper.AccelerationBackend.QNN,
            NativeHelper.AccelerationBackend.GPU -> NativeHelper.AccelerationBackend.GPU
            NativeHelper.AccelerationBackend.CPU -> NativeHelper.AccelerationBackend.CPU
        }
    }

    private fun createFaceLandmarker(delegate: Delegate): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .setDelegate(delegate)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinFaceDetectionConfidence(config.faceDetectionConfidence)
            .setMinFacePresenceConfidence(config.facePresenceConfidence)
            .setMinTrackingConfidence(config.faceTrackingConfidence)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener(this::onFaceDetectionResult)
            .setErrorListener { e -> Log.e(TAG, "FaceLandmarker error", e) }
            .build()
        return FaceLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        if (faceLandmarker == null) {
            initFaceLandmarker()
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCameraUseCases()
                isCameraStarted = true
                broadcastStatus("${if (isFrontCamera) "前置" else "后置"}摄像头已启动")
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
                broadcastStatus("摄像头启动失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        if (isRunning) stopTracking()
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageAnalyzer = null
        isCameraStarted = false
        broadcastStatus("摄像头已关闭")
    }

    private fun switchCamera() {
        isFrontCamera = !isFrontCamera
        if (isCameraStarted) {
            bindCameraUseCases()
            broadcastStatus("已切换到${if (isFrontCamera) "前置" else "后置"}摄像头")
        }
    }

    private fun rebindCameraIfNeeded() {
        if (isCameraStarted) bindCameraUseCases()
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(cameraExecutor, FaceAnalysisAnalyzer()) }

        val surfaceProvider = previewView?.surfaceProvider
        preview = if (surfaceProvider != null) {
            Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
        } else {
            null
        }

        provider.unbindAll()
        camera = if (preview != null) {
            provider.bindToLifecycle(this, selector, preview, imageAnalyzer)
        } else {
            provider.bindToLifecycle(this, selector, imageAnalyzer)
        }
    }

    private fun setPreviewView(view: PreviewView?) {
        previewView = view
        if (isCameraStarted) {
            bindCameraUseCases()
        }
    }

    private fun startTracking() {
        if (!isCameraStarted) startCamera()
        if (faceStreamer?.isConnected != true) {
            faceStreamer = FaceVMCStreamer(config.host, config.oscPort, config.vmcPort)
            if (faceStreamer?.connect() != true) {
                broadcastStatus("推流连接失败: ${config.host}:${config.oscPort}")
                return
            }
            fetchParamMap(config.host)
        }
        isRunning = true
        acquireWakeLock()
        broadcastStatus("推流中 | ${config.host}:${config.oscPort} VMC:${config.vmcPort}")
    }

    private fun stopTracking() {
        isRunning = false
        faceStreamer?.disconnect()
        faceStreamer = null
        releaseWakeLock()
        broadcastStatus("推流已停止")
        if (previewView == null) {
            stopSelf()
        }
    }

    private fun startUvcValidation() {
        isUvcValidationRunning = true
        acquireWakeLock()
        startExternalCameraValidation()
        broadcastStatus("外接摄像头验证已启动")
    }

    private fun stopUvcValidation() {
        isUvcValidationRunning = false
        uvcController?.stop()
        camera2ExternalController?.stop()
        if (!isRunning) {
            releaseWakeLock()
        }
        broadcastStatus("外接摄像头验证已停止")
    }

    private fun startExternalCameraValidation() {
        val externalCameraIds = findExternalCameraIds()
        if (externalCameraIds.isNotEmpty()) {
            uvcController?.stop()
            val controller = camera2ExternalController
                ?: Camera2ExternalCameraController(this) { broadcastStatus("Camera2外部摄像头状态已更新") }
                    .also { camera2ExternalController = it }
            controller.setCameraIds(externalCameraIds, config.cameraInputBindings)
            attachExternalValidationPreviewsToControllers()
            controller.start()
        } else {
            camera2ExternalController?.stop()
            val controller = uvcController
                ?: MultiUvcCameraController(this) { broadcastStatus("UVC外接摄像头状态已更新") }
                    .also { uvcController = it }
            controller.setBindings(config.cameraInputBindings)
            attachExternalValidationPreviewsToControllers()
            controller.start()
        }
    }

    private fun setExternalValidationPreview(role: CameraInputRole, previewView: UVCCameraTextureView?) {
        externalPreviewViews[role] = previewView
        camera2ExternalController?.attachPreview(role, previewView)
        uvcController?.attachPreview(role, previewView)
    }

    private fun attachExternalValidationPreviewsToControllers() {
        CameraInputRole.values().forEach { role ->
            val view = externalPreviewViews[role]
            camera2ExternalController?.attachPreview(role, view)
            uvcController?.attachPreview(role, view)
        }
    }

    private fun externalValidationStatuses(): List<CameraInputStatus> {
        val camera2Statuses = camera2ExternalController?.status().orEmpty()
        return if (camera2Statuses.any { it.camera2Id.isNotEmpty() }) {
            camera2Statuses
        } else {
            uvcController?.status().orEmpty()
        }
    }

    private fun cameraInputOptions(): List<CameraInputDeviceOption> {
        val camera2Options = findExternalCameraIds().map { cameraId ->
            CameraInputDeviceOption(
                sourceType = CameraInputSources.CAMERA2_EXTERNAL,
                deviceId = cameraId,
                displayName = "Camera2 external $cameraId"
            )
        }
        val uvcOptions = uvcController?.deviceOptions().orEmpty()
        return camera2Options + uvcOptions
    }

    private fun findExternalCameraIds(): List<String> {
        return runCatching {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.filter { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_EXTERNAL
            }
        }.getOrElse { error ->
            Log.w(TAG, "External Camera2 enumeration failed", error)
            emptyList()
        }
    }

    private fun fetchParamMap(host: String) {
        Thread {
            try {
                val url = java.net.URL("http://$host:8900/param_map")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val jsonObj = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val floatMap = mutableMapOf<String, String>()
                    jsonObj.optJSONObject("float")?.let { obj ->
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            floatMap[key] = obj.getString(key)
                        }
                    }
                    val binaryMap = mutableMapOf<String, Map<Int, String>>()
                    jsonObj.optJSONObject("binary")?.let { obj ->
                        val groupKeys = obj.keys()
                        while (groupKeys.hasNext()) {
                            val groupKey = groupKeys.next()
                            val bitsObj = obj.getJSONObject(groupKey)
                            val bits = mutableMapOf<Int, String>()
                            val bitKeys = bitsObj.keys()
                            while (bitKeys.hasNext()) {
                                val bitKey = bitKeys.next()
                                bits[bitKey.toInt()] = bitsObj.getString(bitKey)
                            }
                            binaryMap[groupKey] = bits
                        }
                    }
                    faceStreamer?.updateParamMap(floatMap, binaryMap)
                    Log.d(TAG, "Param map fetched: ${floatMap.size} float, ${binaryMap.size} binary")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Param map fetch failed, using default mapping: ${e.message}")
            }
        }.start()
    }

    private fun onFaceDetectionResult(result: FaceLandmarkerResult, input: MPImage) {
        val landmarks = result.faceLandmarks()
        val blendshapesOptional = result.faceBlendshapes()
        faceDetected = landmarks.isNotEmpty()

        if (faceDetected && blendshapesOptional.isPresent) {
            val blendshapes = blendshapesOptional.get()
            if (blendshapes.isNotEmpty()) {
                val faceData = MediaPipeHelper.extractFaceData(
                    landmarks[0], blendshapes[0],
                    config.eyeSensitivity, config.eyeCalibration,
                    config.mouthSensitivity, config.mouthCalibration,
                    false,
                    config.invertEyeX, config.invertEyeY,
                    config.syncEyes, config.sendMergedEyes
                )
                val streamFaceData = MediaPipeHelper.applyEyeRangeCalibration(faceData, config.eyeRangeCalibration)
                faceStreamer?.sendFaceData(streamFaceData)
                tickFps()
                broadcastFaceData(faceData, streamFaceData, blendshapes[0])
            }
        } else {
            tickFps()
            broadcastStatus("未检测到面部")
        }
    }

    private fun tickFps() {
        frameCount++
        val now = System.nanoTime()
        val elapsed = (now - fpsTimestamp) / 1_000_000_000f
        if (elapsed >= 1.0f) {
            currentFps = frameCount / elapsed
            frameCount = 0
            fpsTimestamp = now
        }
    }

    private fun broadcastFaceData(
        rawFaceData: Map<String, Float>,
        streamFaceData: Map<String, Float>,
        blendshapes: List<Category>
    ) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_RUNNING, isRunning)
            putExtra(EXTRA_CAMERA_STARTED, isCameraStarted)
            putExtra(EXTRA_FACE_DETECTED, faceDetected)
            putExtra(EXTRA_FPS, currentFps)
            putExtra(EXTRA_FRONT_CAMERA, isFrontCamera)
            putExtra(EXTRA_RAW_FACE_DATA, HashMap(rawFaceData))
            putExtra(EXTRA_STREAM_FACE_DATA, HashMap(streamFaceData))
            putExtra(EXTRA_BLENDSHAPES, HashMap(blendshapes.associate { it.categoryName() to it.score() }))
            putExtra(EXTRA_UVC_RUNNING, isUvcValidationRunning)
            putExtra(EXTRA_UVC_STATUSES, ArrayList(externalValidationStatuses()))
            putExtra(EXTRA_CAMERA_INPUT_OPTIONS, ArrayList(cameraInputOptions()))
            putStringArrayListExtra(EXTRA_EXTERNAL_CAMERA_IDS, ArrayList(findExternalCameraIds()))
        })
    }

    private fun broadcastStatus(status: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_RUNNING, isRunning)
            putExtra(EXTRA_CAMERA_STARTED, isCameraStarted)
            putExtra(EXTRA_FACE_DETECTED, faceDetected)
            putExtra(EXTRA_FPS, currentFps)
            putExtra(EXTRA_FRONT_CAMERA, isFrontCamera)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_UVC_RUNNING, isUvcValidationRunning)
            putExtra(EXTRA_UVC_STATUSES, ArrayList(externalValidationStatuses()))
            putExtra(EXTRA_CAMERA_INPUT_OPTIONS, ArrayList(cameraInputOptions()))
            putStringArrayListExtra(EXTRA_EXTERNAL_CAMERA_IDS, ArrayList(findExternalCameraIds()))
        })
    }

    private inner class FaceAnalysisAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val landmarker = faceLandmarker
            if (landmarker == null) {
                imageProxy.close()
                return
            }
            try {
                val mediaImage = imageProxy.image ?: return
                val timestamp = System.nanoTime() / 1_000_000
                if (config.isMirrored) {
                    val bitmap = yuvImageToBitmap(mediaImage)
                    val matrix = Matrix().apply { postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f) }
                    val mirroredBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    val mpImage = BitmapImageBuilder(mirroredBitmap).build()
                    landmarker.detectAsync(mpImage, timestamp)
                    bitmap.recycle()
                    if (mirroredBitmap !== bitmap) mirroredBitmap.recycle()
                } else {
                    val mpImage = MediaImageBuilder(mediaImage).build()
                    landmarker.detectAsync(mpImage, timestamp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis error", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun yuvImageToBitmap(image: android.media.Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 90, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FaceTrack:TrackingWakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FaceTrack Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = if (isUvcValidationRunning && !isRunning) {
            ACTION_STOP_UVC_VALIDATION
        } else {
            ACTION_STOP_TRACKING
        }
        val stopLabel = if (isUvcValidationRunning && !isRunning) "停止验证" else "停止推流"
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FaceTrackingService::class.java).setAction(stopAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("FaceTrack 正在运行")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(isRunning || isUvcValidationRunning)
            .addAction(android.R.drawable.ic_media_pause, stopLabel, stopIntent)
            .build()
    }
}
