package com.facetrack.standalone

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FaceTrack"
    }

    // ===== 配置参数 =====
    private var config = AppConfig()

    // ===== 摄像头相关 =====
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isCameraStarted = false
    private var isFrontCamera = true
    private var hasFrontCamera = false
    private var hasBackCamera = false

    // ===== MediaPipe 面部识别 =====
    private var faceLandmarker: FaceLandmarker? = null
    private var isModelLoaded = false

    // ===== 推流 =====
    private var faceStreamer: FaceVMCStreamer? = null

    // ===== UI 控件 =====
    private lateinit var viewFinder: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var btnCamera: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnClosePanel: ImageButton
    private lateinit var settingsPanel: LinearLayout
    private lateinit var etOscHost: EditText
    private lateinit var etOscPort: EditText
    private lateinit var etVmcPort: EditText
    private lateinit var spinnerBackend: Spinner
    private lateinit var tvBackendInfo: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnCalibrate: Button

    // ===== 状态 =====
    private var isRunning = false
    private var isPanelOpen = false
    private var faceDetected = false
    private var frameCount = 0L
    private var fpsTimestamp = System.nanoTime()
    private var currentFps = 0f
    private var currentBackend: NativeHelper.AccelerationBackend = NativeHelper.AccelerationBackend.AUTO
    private var actualDelegateLabel: String = ""  // 实际使用的 Delegate 标签

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        tvStatus = findViewById(R.id.tvStatus)
        btnSettings = findViewById(R.id.btnSettings)
        btnCamera = findViewById(R.id.btnCamera)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnClosePanel = findViewById(R.id.btnClosePanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        etOscHost = findViewById(R.id.etOscHost)
        etOscPort = findViewById(R.id.etOscPort)
        etVmcPort = findViewById(R.id.etVmcPort)
        spinnerBackend = findViewById(R.id.spinnerBackend)
        tvBackendInfo = findViewById(R.id.tvBackendInfo)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnCalibrate = findViewById(R.id.btnCalibrate)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // 设置按钮 - 切换二级菜单
        btnSettings.setOnClickListener {
            toggleSettingsPanel()
        }

        // 关闭面板按钮
        btnClosePanel.setOnClickListener {
            closeSettingsPanel()
        }

        // 摄像头开关
        btnCamera.setOnClickListener {
            if (isCameraStarted) stopCamera() else startCameraWithCheck()
        }

        // 切换前后摄像头
        btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        // 推流开关
        btnStartStop.setOnClickListener {
            if (isRunning) stopTracking() else startTracking()
        }

        // 校准
        btnCalibrate.setOnClickListener {
            Toast.makeText(this, "校准面部", Toast.LENGTH_SHORT).show()
        }

        // 推理硬件选择
        setupBackendSpinner()

        // 进入时摄像头关闭，推流按钮禁用
        btnStartStop.isEnabled = false
        updateStatus("模型加载中...")

        Log.d(TAG, "UI setup complete")
    }

    /**
     * 切换二级菜单面板显示
     */
    private fun toggleSettingsPanel() {
        if (isPanelOpen) closeSettingsPanel() else openSettingsPanel()
    }

    private fun openSettingsPanel() {
        settingsPanel.visibility = View.VISIBLE
        btnSettings.setImageResource(android.R.drawable.ic_menu_revert)
        isPanelOpen = true
    }

    private fun closeSettingsPanel() {
        settingsPanel.visibility = View.GONE
        btnSettings.setImageResource(android.R.drawable.ic_menu_preferences)
        isPanelOpen = false
    }

    /**
     * 初始化推理硬件 Spinner
     *
     * 选项: 自动(推荐) / GPU / NPU(实验性) / QNN(骁龙) / CPU
     * - AUTO: 根据设备能力自动选择最优后端 (QNN > NNAPI > GPU > CPU)
     * - GPU: MediaPipe GPU Delegate (OpenGL ES / OpenCL)
     * - NPU: MediaPipe 不直接支持 NNAPI，降级到 GPU
     * - QNN: 骁龙 NPU 专用，需集成 QNN SDK 库，否则降级到 GPU
     * - CPU: 纯 CPU 推理
     */
    private fun setupBackendSpinner() {
        val deviceSummary = NativeHelper.getDeviceSummary()
        val caps = deviceSummary.accelerationCaps

        val gpuSupported = (caps and NativeHelper.CAP_GPU) != 0
        val npuSupported = (caps and NativeHelper.CAP_NPU) != 0
        val qnnSupported = NativeHelper.isQnnAvailable()

        // 构建选项列表
        val backendItems = mutableListOf<String>()
        val backendValues = mutableListOf<NativeHelper.AccelerationBackend>()

        // 自动(推荐) - 始终显示
        val autoHint = when {
            qnnSupported -> "自动(推荐) → QNN"
            npuSupported && gpuSupported -> "自动(推荐) → GPU"
            gpuSupported -> "自动(推荐) → GPU"
            else -> "自动(推荐) → CPU"
        }
        backendItems.add(autoHint)
        backendValues.add(NativeHelper.AccelerationBackend.AUTO)

        // GPU
        val gpuLabel = if (!gpuSupported) {
            "GPU - 不可用"
        } else if (NativeHelper.isGpuCompatibilityRisky()) {
            "GPU (Mali兼容性风险)"
        } else {
            "GPU"
        }
        backendItems.add(gpuLabel)
        backendValues.add(NativeHelper.AccelerationBackend.GPU)

        // NPU (NNAPI) - 实验性
        if (npuSupported) {
            backendItems.add("NPU (实验性)")
            backendValues.add(NativeHelper.AccelerationBackend.NNAPI)
        }

        // QNN (骁龙) - 仅骁龙设备显示
        if (qnnSupported) {
            backendItems.add("QNN (骁龙NPU)")
            backendValues.add(NativeHelper.AccelerationBackend.QNN)
        }

        // CPU
        backendItems.add("CPU")
        backendValues.add(NativeHelper.AccelerationBackend.CPU)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, backendItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBackend.adapter = adapter

        // 默认选中 AUTO
        spinnerBackend.setSelection(0)

        // 更新硬件信息
        updateBackendInfo(deviceSummary)

        // 选择变更时切换推理硬件
        spinnerBackend.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = backendValues[position]
                if (selected == currentBackend) return

                // 推流中不允许切换
                if (isRunning) {
                    Toast.makeText(this@MainActivity, "请先停止推流再切换硬件", Toast.LENGTH_SHORT).show()
                    val restoreIndex = backendValues.indexOf(currentBackend)
                    if (restoreIndex >= 0) spinnerBackend.setSelection(restoreIndex)
                    return
                }

                switchBackend(selected)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    /**
     * 更新硬件信息提示文本
     */
    private fun updateBackendInfo(summary: NativeHelper.DeviceSummary) {
        val parts = mutableListOf<String>()
        if (NativeHelper.isQnnAvailable()) parts.add("QNN✓")
        if ((summary.accelerationCaps and NativeHelper.CAP_NPU) != 0) parts.add("NPU✓")
        if ((summary.accelerationCaps and NativeHelper.CAP_GPU) != 0) parts.add("GPU✓")
        val info = buildString {
            append(summary.socDescription)
            if (summary.isFlagship2024) append(" [旗舰]")
            if (parts.isNotEmpty()) append(" | ${parts.joinToString(" ")}")
        }
        tvBackendInfo.text = info
    }

    /**
     * 切换推理硬件后端
     *
     * MediaPipe Tasks Vision Delegate 仅支持 GPU/CPU:
     * - AUTO: 解析为实际后端后递归调用
     * - GPU: 直接使用 Delegate.GPU
     * - NNAPI: 降级到 Delegate.GPU (MediaPipe 不支持 NNAPI)
     * - QNN: 降级到 Delegate.GPU (需 TFLite QNN Delegate，MediaPipe 不支持)
     * - CPU: 直接使用 Delegate.CPU
     */
    private fun switchBackend(backend: NativeHelper.AccelerationBackend) {
        // AUTO: 解析为实际后端
        if (backend == NativeHelper.AccelerationBackend.AUTO) {
            val resolved = NativeHelper.resolveAutoBackend()
            Log.d(TAG, "AUTO resolved to $resolved")
            currentBackend = NativeHelper.AccelerationBackend.AUTO
            switchBackend(resolved)
            return
        }

        val wasCameraStarted = isCameraStarted
        if (wasCameraStarted) stopCamera()

        // 关闭旧模型
        faceLandmarker?.close()
        faceLandmarker = null
        isModelLoaded = false

        // 确定实际使用的 Delegate 和显示标签
        val delegate: Delegate
        val displayName: String

        when (backend) {
            NativeHelper.AccelerationBackend.GPU -> {
                delegate = Delegate.GPU
                displayName = "GPU"
            }
            NativeHelper.AccelerationBackend.NNAPI -> {
                // MediaPipe 不支持 NNAPI Delegate，降级到 GPU
                delegate = Delegate.GPU
                displayName = "NPU→GPU"
                Log.w(TAG, "NNAPI not supported by MediaPipe Tasks, falling back to GPU delegate")
            }
            NativeHelper.AccelerationBackend.QNN -> {
                // QNN Delegate 需要 TFLite 直接调用，MediaPipe 不支持，降级到 GPU
                delegate = Delegate.GPU
                displayName = "QNN→GPU"
                Log.w(TAG, "QNN not supported by MediaPipe Tasks, falling back to GPU delegate")
            }
            NativeHelper.AccelerationBackend.CPU -> {
                delegate = Delegate.CPU
                displayName = "CPU"
            }
            NativeHelper.AccelerationBackend.AUTO -> {
                // 不应到达这里，AUTO 在上面已解析
                delegate = Delegate.GPU
                displayName = "AUTO→GPU"
            }
        }

        // 如果是 AUTO 模式，标签加上实际后端
        val fullLabel = if (currentBackend == NativeHelper.AccelerationBackend.AUTO) {
            "自动($displayName)"
        } else {
            displayName
        }

        updateStatus("切换到 $fullLabel ...")

        try {
            ModelHelper.loadModelFile(this)

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

            faceLandmarker = FaceLandmarker.createFromOptions(this, options)
            isModelLoaded = true
            actualDelegateLabel = fullLabel

            // NNAPI/QNN 降级提示
            if (backend == NativeHelper.AccelerationBackend.NNAPI || backend == NativeHelper.AccelerationBackend.QNN) {
                Toast.makeText(this, "$displayName: MediaPipe 暂不支持 NPU 直调，使用 GPU 加速", Toast.LENGTH_LONG).show()
            }

            Log.d(TAG, "FaceLandmarker switched to $fullLabel (delegate=$delegate)")
            val camInfo = buildCameraInfoText()
            updateStatus("已切换到 $fullLabel | $camInfo")

        } catch (e: Exception) {
            Log.e(TAG, "$fullLabel loading failed", e)
            // 降级到 CPU
            if (delegate != Delegate.CPU) {
                val maliHint = if (NativeHelper.isMaliGpu()) " (Mali GPU 驱动兼容性问题)" else ""
                Toast.makeText(this, "$fullLabel 不可用$maliHint，降级到 CPU", Toast.LENGTH_LONG).show()
                currentBackend = backend
                switchBackend(NativeHelper.AccelerationBackend.CPU)
                val cpuIndex = spinnerBackend.adapter?.let { adapter ->
                    (0 until adapter.count).firstOrNull {
                        (adapter.getItem(it) as? String)?.startsWith("CPU") == true
                    }
                } ?: 0
                spinnerBackend.setSelection(cpuIndex)
            } else {
                Toast.makeText(this, "模型加载失败", Toast.LENGTH_LONG).show()
                updateStatus("模型加载失败")
            }
        }

        // 恢复摄像头
        if (wasCameraStarted && isModelLoaded) {
            startCamera()
        }
    }

    /**
     * 检测设备可用摄像头
     */
    private fun detectCameras() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                when (facing) {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> hasFrontCamera = true
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK -> hasBackCamera = true
                }
            }
            Log.d(TAG, "Camera detection: front=$hasFrontCamera, back=$hasBackCamera")
        } catch (e: Exception) {
            Log.e(TAG, "Camera detection failed", e)
        }
    }

    /**
     * 从输入框读取推流配置
     */
    private fun readConfigFromUI() {
        val host = etOscHost.text.toString().trim().ifEmpty { "127.0.0.1" }
        val oscPort = etOscPort.text.toString().trim().toIntOrNull() ?: 9000
        val vmcPort = etVmcPort.text.toString().trim().toIntOrNull() ?: 39539

        config = config.copy(host = host, oscPort = oscPort, vmcPort = vmcPort)
        Log.d(TAG, "Config updated: OSC $host:$oscPort, VMC port $vmcPort")
    }

    private fun checkPermissions() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            initFaceLandmarker()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            initFaceLandmarker()
        } else {
            Toast.makeText(this, "需要摄像头权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun initFaceLandmarker() {
        // 检测可用摄像头
        detectCameras()

        // 使用当前选择的后端（默认为推荐后端）
        switchBackend(currentBackend)
    }

    private fun buildCameraInfoText(): String {
        val parts = mutableListOf<String>()
        if (hasFrontCamera) parts.add("前置")
        if (hasBackCamera) parts.add("后置")
        if (parts.isEmpty()) parts.add("无摄像头")
        return "可用: ${parts.joinToString("+")} | 点击左下角开启"
    }

    private fun startCameraWithCheck() {
        if (!isModelLoaded) {
            Toast.makeText(this, "模型未加载", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasFrontCamera && !hasBackCamera) {
            Toast.makeText(this, "未检测到可用摄像头", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 100)
            return
        }
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // 优先使用前置，不可用则用后置
                val cameraSelector = if (isFrontCamera && hasFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else if (hasBackCamera) {
                    isFrontCamera = false
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                bindCameraUseCases(cameraSelector)

                isCameraStarted = true
                btnCamera.setImageResource(android.R.drawable.ic_menu_crop)
                btnStartStop.isEnabled = true

                // 有前后双摄时显示切换按钮
                btnSwitchCamera.visibility = if (hasFrontCamera && hasBackCamera) View.VISIBLE else View.GONE

                val camLabel = if (isFrontCamera) "前置" else "后置"
                updateStatus("${camLabel}摄像头已启动 | 等待面部...")

            } catch (e: Exception) {
                Log.e(TAG, "Camera startup failed", e)
                Toast.makeText(this, "摄像头启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraSelector: CameraSelector) {
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, FaceAnalysisAnalyzer())
            }

        cameraProvider?.unbindAll()

        camera = cameraProvider?.bindToLifecycle(
            this, cameraSelector, preview, imageAnalyzer
        )
    }

    /**
     * 切换前后摄像头
     */
    private fun switchCamera() {
        if (!isCameraStarted) return
        if (!hasFrontCamera || !hasBackCamera) {
            Toast.makeText(this, "设备仅有一个摄像头", Toast.LENGTH_SHORT).show()
            return
        }

        isFrontCamera = !isFrontCamera
        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            bindCameraUseCases(cameraSelector)
            val camLabel = if (isFrontCamera) "前置" else "后置"
            updateStatus("已切换到${camLabel}摄像头 | 等待面部...")
            Log.d(TAG, "Switched to ${camLabel} camera")
        } catch (e: Exception) {
            Log.e(TAG, "Camera switch failed", e)
            Toast.makeText(this, "切换摄像头失败", Toast.LENGTH_SHORT).show()
            // 切换失败时恢复状态
            isFrontCamera = !isFrontCamera
        }
    }

    private fun stopCamera() {
        if (isRunning) stopTracking()

        cameraProvider?.unbindAll()
        isCameraStarted = false
        btnCamera.setImageResource(android.R.drawable.ic_menu_camera)
        btnStartStop.isEnabled = false
        btnSwitchCamera.visibility = View.GONE
        updateStatus("摄像头已关闭")
        Log.d(TAG, "Camera stopped")
    }

    private inner class FaceAnalysisAnalyzer : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            if (!isModelLoaded || faceLandmarker == null) {
                imageProxy.close()
                return
            }

            try {
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return
                }

                val mpImage: MPImage = MediaImageBuilder(mediaImage).build()
                val timestamp = System.nanoTime() / 1_000_000
                faceLandmarker?.detectAsync(mpImage, timestamp)

            } catch (e: Exception) {
                Log.e(TAG, "Analysis error", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun onFaceDetectionResult(result: FaceLandmarkerResult, input: MPImage) {
        val landmarks = result.faceLandmarks()
        val blendshapesOptional = result.faceBlendshapes()

        faceDetected = landmarks.isNotEmpty()

        if (faceDetected && blendshapesOptional.isPresent) {
            val faceBlendshapes = blendshapesOptional.get()
            if (faceBlendshapes.isNotEmpty()) {
                val faceData = MediaPipeHelper.extractFaceData(
                    landmarks[0], faceBlendshapes[0]
                )

                faceStreamer?.sendFaceData(faceData)
                updateFaceStatus(faceData)
            }
        } else {
            updateStatus("未检测到面部")
        }

        frameCount++
        val now = System.nanoTime()
        val elapsed = (now - fpsTimestamp) / 1_000_000_000f
        if (elapsed >= 1.0f) {
            currentFps = frameCount / elapsed
            frameCount = 0
            fpsTimestamp = now
        }
    }

    private fun startTracking() {
        if (!isModelLoaded) {
            Toast.makeText(this, "模型未加载", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isCameraStarted) {
            Toast.makeText(this, "请先开启摄像头", Toast.LENGTH_SHORT).show()
            return
        }

        readConfigFromUI()

        faceStreamer = FaceVMCStreamer(config.host, config.oscPort, config.vmcPort)
        if (faceStreamer?.connect() == true) {
            isRunning = true
            updateStatus("推流中 | ${config.host}:${config.oscPort} VMC:${config.vmcPort}")
            btnStartStop.text = "停止推流"
            etOscHost.isEnabled = false
            etOscPort.isEnabled = false
            etVmcPort.isEnabled = false
            Log.d(TAG, "Tracking started: ${config.host}:${config.oscPort}, VMC:${config.vmcPort}")
        } else {
            Toast.makeText(this, "推流连接失败: ${config.host}:${config.oscPort}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopTracking() {
        isRunning = false
        faceStreamer?.disconnect()
        faceStreamer = null
        btnStartStop.text = "启动推流"
        etOscHost.isEnabled = true
        etOscPort.isEnabled = true
        etVmcPort.isEnabled = true
        updateStatus("推流已停止")
        Log.d(TAG, "Tracking stopped")
    }

    private fun updateStatus(statusText: String) {
        runOnUiThread { tvStatus.text = statusText }
    }

    private fun updateFaceStatus(faceData: Map<String, Float>) {
        runOnUiThread {
            val mouth = faceData["v2/JawOpen"]?.let { String.format("%.2f", it) } ?: "-"
            val smile = faceData["v2/MouthSmileLeft"]?.let { String.format("%.2f", it) } ?: "-"
            val label = actualDelegateLabel.ifEmpty { "CPU" }
            tvStatus.text = "[$label] FPS: ${currentFps.toInt()} | 嘴: $mouth | 笑: $smile"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceLandmarker?.close()
        faceStreamer?.disconnect()
        cameraExecutor.shutdown()
        Log.d(TAG, "Activity destroyed")
    }
}
