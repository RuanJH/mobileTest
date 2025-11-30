// 在 build.gradle 顶部添加（与 dependencies 同级）
configurations {
    all {
        // 排除所有依赖中重复的 net.sf.scuba 包（保留 scuba-smartcards 的版本）
        exclude group: 'net.sf.scuba', module: 'scuba-smartcards'
        // 排除 szca-auth-eid 中内置的 cert-cvc 和 jmrtd
        exclude group: 'org.ejbca.cvc', module: 'cert-cvc'
        exclude group: 'org.jmrtd', module: 'jmrtd'
    }
}


dependencies {
    implementation('com.hsbc.mobilebanking.android.external.one-connect-sdk:eid-travel-bc:1.0.3.2') {
        exclude group: 'net.sf.scuba', module: 'scuba-smartcards'
    }
    
    implementation('com.hsbc.mobilebanking.android.external.one-connect-sdk:szca-auth-eid:1.1.0.1') {
        exclude group: 'org.ejbca.cvc', module: 'cert-cvc'
        exclude group: 'org.jmrtd', module: 'jmrtd'
    }
    
    // 或者如果 scuba-smartcards 是独立依赖，可以排除它
    implementation('net.sf.scuba:scuba-smartcards:0.0.20') {
        exclude group: 'net.sf.scuba.data'
        exclude group: 'net.sf.scuba.smartcards'
        exclude group: 'net.sf.scuba.tlv'
        exclude group: 'net.sf.scuba.util'
    }
}




package com.pa.cardcheck.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.Rect
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pa.cardcheck.R
import com.pa.cardcheck.bean.CardResult
import com.pa.cardcheck.callback.FrameCallback
import com.pa.cardcheck.consts.Constants
import com.pa.cardcheck.enums.PhotoFrameShape
import com.pa.cardcheck.event.OcrEvent
import com.pa.cardcheck.net.ApiCreater
import com.pa.cardcheck.net.request.OcrRequestBody
import com.pa.cardcheck.net.response.OcrResponse
import com.pa.cardcheck.utils.PaBitmapUtil
import com.pa.cardcheck.utils.PaDisplayUtil
import com.pa.cardcheck.utils.PaFileUtil
import com.pa.cardcheck.utils.PaLogger
import com.pingan.idverify.VerticesDetectionResultNew
import com.pingan.idverify.bean.IdCardType
import com.pingan.idverify.bean.Identification18Result
import com.pingan.idverify.bean.QualityInspectionOptionParams
import com.pingan.idverify.bean.QualityInspectionResult
import com.pingan.idverify.detector.PaDetectorManager
import org.greenrobot.eventbus.EventBus
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class Camera18CardComposeActivity : ComponentActivity() {
    
    private val TAG = "Camera18CardCompose"
    
    // 状态变量
    private var idCardType by mutableStateOf(IdCardType.ERROR_INVALID_ID_CARD)
    private var photoIndex by mutableStateOf(0)
    private var errorMessage by mutableStateOf("")
    private var hintMessage by mutableStateOf("请将身份证放入框内")
    private var isLoading by mutableStateOf(false)
    private var isPhotoFrameVisible by mutableStateOf(false)
    private var isPhotoFrameSuccess by mutableStateOf(false)
    
    // 图片数据
    private lateinit var correctedImage1: Bitmap
    private lateinit var correctedImage2: Bitmap
    private lateinit var correctedImage3: Bitmap
    private lateinit var imageDigest1: String
    private lateinit var imageDigest2: String
    private lateinit var imageDigest3: String
    private lateinit var imageBase641: String
    private lateinit var imageBase642: String
    private lateinit var imageBase643: String
    
    // CameraX 相关
    private lateinit var imageCapture: ImageCapture
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    
    // 其他变量
    private var previewViewSize: Point = Point(0, 0)
    private var photoFrameVerticesAfterResize: Array<Point> = emptyArray()
    private var isScreenLandscape = false
    private var qualityPassed = false
    private var startCaptureTime = 0L
    private var openLightTime = 0L
    
    // 常量
    private companion object {
        const val SCREEN_RECT_WIDTH_FINAL_PORTRAIT = 340
        const val SCREEN_RECT_WIDTH_FINAL_LANDSCAPE = 430
        const val SCREEN_RECT_HEIGHT_FINAL_PORTRAIT = 215
        const val SCREEN_RECT_HEIGHT_FINAL_LANDSCAPE = 272
        const val INTERVAL_FROM_OPEN_LIGHT_TO_START_CAPTURE = 200L
        const val INTERVAL_FROM_START_CAPTURE_TO_CLOSE_LIGHT = 600L
        const val INTERVAL_FROM_START_CAPTURE_TO_END_CAPTURE = 700L
        const val LIGHT_CAPTURE_MAX_COUNT = 30
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 读取屏幕方向设置
        val sp = getSharedPreferences("setting", Context.MODE_PRIVATE)
        val orientation = sp.getInt("screen_orientation", Constants.ORIENTATION_PORTRAIT)
        isScreenLandscape = orientation == Constants.ORIENTATION_LANDSCAPE
        
        setContent {
            Camera18CardComposeApp()
        }
        
        initializeCamera()
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Camera18CardComposeApp() {
        MaterialTheme {
            Scaffold(
                topBar = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "身份证识别",
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.CenterStart),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                bottomBar = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            if (errorMessage.isNotEmpty()) {
                                Text(
                                    text = errorMessage,
                                    color = Color.Red,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            Text(
                                text = hintMessage,
                                color = Color.White
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 相机预览
                    CameraPreviewView(
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // 拍照框
                    if (isPhotoFrameVisible) {
                        PhotoFrameView(
                            isSuccess = isPhotoFrameSuccess,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(
                                    width = if (isScreenLandscape) 430.dp else 340.dp,
                                    height = if (isScreenLandscape) 272.dp else 215.dp
                                )
                        )
                    }
                    
                    // 加载指示器
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Text(
                                    text = "正在识别...",
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun CameraPreviewView(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = modifier,
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    
                    // 预览
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    
                    // 图像分析
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                analyzeImage(imageProxy)
                                imageProxy.close()
                            }
                        }
                    
                    // 图像捕获
                    imageCapture = ImageCapture.Builder().build()
                    
                    // 选择后置摄像头
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    try {
                        // 解除绑定后再绑定
                        cameraProvider?.unbindAll()
                        cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
    }
    
    @Composable
    fun PhotoFrameView(isSuccess: Boolean, modifier: Modifier = Modifier) {
        val borderColor = if (isSuccess) Color.Green else Color.Red
        
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Transparent)
        ) {
            // 绘制边框
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Transparent)
            )
        }
    }
    
    private fun initializeCamera() {
        // CameraX 初始化在 CameraPreviewView 中完成
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(imageProxy: ImageProxy) {
        try {
            when (photoIndex) {
                0 -> processFirstImage(imageProxy)
                1 -> processFlashImages(imageProxy)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
        }
    }
    
    private fun processFirstImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap() ?: return
        
        // 新旧卡检测
        val cardType = PaDetectorManager.getIdCardType(
            this,
            bitmap,
            previewViewSize,
            photoFrameVerticesAfterResize
        )
        
        updateUiByIdCardType(cardType.result)
        idCardType = cardType.result
        
        if (idCardType == IdCardType.ID_CARD_TYPE_18) {
            // 第一张做质量检测
            val qualityResult = checkQuality(bitmap)
            updateUiByQualityInspectionResult(qualityResult, false)
            
            if (qualityResult?.result != QualityInspectionResult.SUCCESS) {
                PaLogger.d("Camera18Card", "普通光图片质量不合格，重新检测")
                return
            }
            
            // 保存第一张照片
            correctedImage1 = qualityResult.correctBitmap
            imageDigest1 = qualityResult.correctImageDigest
            imageBase641 = qualityResult.correctImageBase64
            
            // 释放bitmap
            PaBitmapUtil.releaseBitmaps(bitmap)
            
            // 进入闪光灯模式
            photoIndex++
            openLightTime = System.currentTimeMillis()
            hintMessage = "开启闪光灯检测"
        }
    }
    
    private fun processFlashImages(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // 检查是否达到开始抓图时间
        if (currentTime - openLightTime >= INTERVAL_FROM_OPEN_LIGHT_TO_START_CAPTURE) {
            if (startCaptureTime == 0L) {
                startCaptureTime = currentTime
                hintMessage = "开始闪光灯抓图"
            }
            
            // 处理闪光灯图片逻辑
            // 这里简化处理，实际需要实现完整的闪光灯检测逻辑
            
            // 检查是否结束
            if (currentTime - startCaptureTime >= INTERVAL_FROM_START_CAPTURE_TO_END_CAPTURE) {
                completeFlashCapture()
            }
        }
    }
    
    private fun completeFlashCapture() {
        // 这里简化处理，实际需要选择两张最佳闪光灯图片
        // 模拟完成闪光灯拍摄
        photoIndex++
        hintMessage = "拍摄完成，正在处理..."
        
        // 执行鉴伪和OCR
        doIdentificationAndOcrDetect()
    }
    
    private fun checkQuality(rawBitmap: Bitmap): QualityInspectionResult? {
        val optionParams = QualityInspectionOptionParams().apply {
            needCheckLightSpot = true
            needCheckGray = true
            needControlAngle = false
            flashCheck = false
            needCheckWindow = true
        }
        
        return PaDetectorManager.do18QualityInspection(
            this,
            rawBitmap,
            previewViewSize,
            photoFrameVerticesAfterResize,
            optionParams
        )
    }
    
    private fun updateUiByIdCardType(idCardType: Int) {
        when (idCardType) {
            IdCardType.ID_CARD_TYPE_03 -> {
                handleError("仅支持18版身份证检测")
                setPhotoFrame(false)
            }
            IdCardType.ID_CARD_TYPE_18 -> {
                clearError()
                setPhotoFrame(false)
            }
            IdCardType.ERROR_INVALID_ID_CARD -> {
                handleError("无效的身份证")
                setPhotoFrame(false)
            }
            else -> {
                // 处理其他错误类型
                handleError("检测异常")
                setPhotoFrame(false)
            }
        }
    }
    
    private fun updateUiByQualityInspectionResult(
        qualityResult: QualityInspectionResult?,
        isCheckIdCardType: Boolean
    ) {
        when (qualityResult?.result) {
            QualityInspectionResult.SUCCESS -> {
                clearError()
                setPhotoFrame(!isCheckIdCardType)
                vibrate()
            }
            else -> {
                handleError(getQualityErrorMessage(qualityResult))
                setPhotoFrame(false)
            }
        }
    }
    
    private fun getQualityErrorMessage(qualityResult: QualityInspectionResult?): String {
        return when (qualityResult?.result) {
            QualityInspectionResult.NO_CARD_DETECTED -> "未检测到身份证"
            QualityInspectionResult.TOO_FAR_OUT_BORDER -> "请将身份证靠近边框"
            QualityInspectionResult.TOO_FAR_IN_BORDER -> "请将身份证放远一些"
            QualityInspectionResult.TOO_DARK -> "光线太暗"
            QualityInspectionResult.TOO_BRIGHT -> "光线太强"
            QualityInspectionResult.HAS_LIGHT_SPOT -> "检测到光斑"
            QualityInspectionResult.IS_GRAY_COPY -> "检测到黑白复印件"
            QualityInspectionResult.BLURRING -> "图像不清晰"
            QualityInspectionResult.NO_WINDOW -> "未检测到透明窗口"
            else -> "质量检测失败"
        }
    }
    
    private fun setPhotoFrame(isSuccess: Boolean) {
        isPhotoFrameVisible = true
        isPhotoFrameSuccess = isSuccess
    }
    
    private fun handleError(msg: String) {
        errorMessage = msg
        isPhotoFrameVisible = true
        isPhotoFrameSuccess = false
    }
    
    private fun clearError() {
        errorMessage = ""
    }
    
    private fun vibrate() {
        // 使用 ToneGenerator 模拟振动反馈
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP)
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate failed", e)
        }
    }
    
    private fun doIdentificationAndOcrDetect() {
        isLoading = true
        hintMessage = "正在进行鉴伪和OCR识别..."
        
        // 模拟鉴伪过程
        // 实际应该调用 PaDetectorManager.do18Identification
        val identificationResult = Identification18Result().apply {
            success = true
        }
        
        completeShot(identificationResult)
    }
    
    private fun completeShot(identificationResult: Identification18Result) {
        // OCR 识别
        performOcr(identificationResult)
    }
    
    private fun performOcr(identificationResult: Identification18Result) {
        val requestBody = OcrRequestBody().apply {
            seeThroughFlag = "1"
            file.images = arrayOf(imageBase641, imageBase643, imageBase641, imageBase641)
            file.tokens = arrayOf(imageDigest1, imageDigest3, imageDigest1, imageDigest1)
        }
        
        ApiCreater.create(this).ocr(requestBody)
            .enqueue(object : Callback<OcrResponse> {
                override fun onResponse(call: Call<OcrResponse>, response: Response<OcrResponse>) {
                    val ocrResponse = response.body()
                    if (ocrResponse?.data != null) {
                        EventBus.getDefault().postSticky(
                            OcrEvent(true, idCardType, ocrResponse)
                        )
                    }
                    navigateToResult(identificationResult)
                }
                
                override fun onFailure(call: Call<OcrResponse>, t: Throwable) {
                    PaLogger.e("OCR", "OCR failed: ${t.message}")
                    EventBus.getDefault().postSticky(OcrEvent(true))
                    navigateToResult(identificationResult)
                }
            })
    }
    
    private fun navigateToResult(identificationResult: Identification18Result) {
        val cardResult = CardResult().apply {
            cardType = 2
            allPassed = identificationResult.success
            hologramPassed = identificationResult.errorCodes?.contains(
                Identification18Result.NO_HOLOGRAM
            ) != true
        }
        
        EventBus.getDefault().postSticky(cardResult)
        // 启动结果页面
        // startActivity(ResultActivity.makeIntent(this, cardResult))
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}

// 扩展函数：ImageProxy 转 Bitmap
fun ImageProxy.toBitmap(): Bitmap? {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
