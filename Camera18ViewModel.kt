// Camera18ViewModel.kt
package com.your.package // 改为你的包名

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingan.idverify.bean.IdCardType
import com.pingan.idverify.bean.Identification18Result
import com.pingan.idverify.bean.QualityInspectionOptionParams
import com.pingan.idverify.bean.QualityInspectionResult
import com.pingan.idverify.detector.PaDetectorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import com.pa.cardcheck.bean.CardResult
import com.pa.cardcheck.net.ApiCreater
import com.pa.cardcheck.net.request.OcrRequestBody
import com.pa.cardcheck.net.response.OcrResponse
import com.pa.cardcheck.event.OcrEvent
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * ViewModel: 承担全部识别逻辑
 *
 * 公开函数：
 *  - onImageProxy(imageProxy)  // 在 Analyzer 中调用
 *  - startDetection(previewSize, vertices) // 在 screen 初始化后调用用于设置 preview size & vertices
 *  - reset() // 重置流程
 *
 * Results via callback:
 *  - onFinished: (CardResult) -> Unit
 *  - onHint: (String) -> Unit
 */
class Camera18ViewModel(
    application: Application,
    private val onFinished: (CardResult) -> Unit,
    private val onHint: (String) -> Unit
) : AndroidViewModel(application) {

    private val TAG = "Camera18ViewModel"

    // constants
    private val SCREEN_RECT_WIDTH_FINAL_PORTRAIT = 340
    private val SCREEN_RECT_WIDTH_FINAL_LANDSCAPE = 430
    private val SCREEN_RECT_HEIGHT_FINAL_PORTRAIT = 215
    private val SCREEN_RECT_HEIGHT_FINAL_LANDSCAPE = 272

    private val LIGHT_CAPTURE_MAX_COUNT = 30

    // state
    private var idCardTypeResult: Int = IdCardType.ERROR_INVALID_ID_CARD
    private var photoIndex = 0

    // corrected images & digests & base64
    private var correctedImage1: Bitmap? = null
    private var correctedImage2: Bitmap? = null
    private var correctedImage3: Bitmap? = null
    private var imageDigest1: String? = null
    private var imageDigest2: String? = null
    private var imageDigest3: String? = null
    private var imageBase641: String? = null
    private var imageBase642: String? = null
    private var imageBase643: String? = null

    // flash capture storage: Pair<path, brightnessScore>
    private val normalFlashFilePaths = Collections.synchronizedList(ArrayList<Pair<String, Float>>())
    private val normalFlashEdgeResult = Collections.synchronizedList(ArrayList<Int>())
    private val lightCaptureProgressCount = AtomicInteger(0)

    // timing
    private var openLightTime: Long = 0
    private var startCaptureTime: Long = 0
    private val intervalFromOpenLightToStartCapture = 200L
    private val intervalFromStartCaptureToEndCapture = 700L
    private val intervalFromStartCaptureToCloseLight = 600L

    // thresholds
    private val lightThresholds = floatArrayOf(100f, 220f)

    // preview & vertices passed from Screen
    private var previewViewSize = Point(0, 0)
    private var previewViewSizeAfterResize = Point(0, 0)
    private var photoFrameVerticesAfterResize: Array<Point> = arrayOf()

    // control flags
    private var needGetPreviewFrame = false
    private var isOperatingPicture = false
    private var frameTotal: Long = 0
    private var qualityPassed = false
    private var saveCorrectedImage = true

    // cache dir prefix
    private var mDetectImgDirName: String? = null

    // executor for heavy jobs
    private val singleExecutor = Executors.newSingleThreadExecutor()

    // helper: set by screen
    var isScreenLandscape: Boolean = false

    // public setters
    fun startDetection(previewSize: Point, vertices: Array<Point>, density: Float, isLandscape: Boolean) {
        this.previewViewSize = previewSize
        // keep after resize same as preview for CameraX (adjust if you need cropping)
        this.previewViewSizeAfterResize = previewSize
        this.photoFrameVerticesAfterResize = vertices
        this.isScreenLandscape = isLandscape
        this.needGetPreviewFrame = true
    }

    fun reset() {
        photoIndex = 0
        idCardTypeResult = IdCardType.ERROR_INVALID_ID_CARD
        startCaptureTime = 0
        qualityPassed = false
        openLightTime = 0
        normalFlashFilePaths.clear()
        normalFlashEdgeResult.clear()
        lightCaptureProgressCount.set(0)
        mDetectImgDirName = null
        needGetPreviewFrame = true
        isOperatingPicture = false
    }

    /**
     * Analyzer 将每帧的 ImageProxy 传进来
     */
    fun onImageProxy(imageProxy: ImageProxy) {
        frameTotal++
        if (!needGetPreviewFrame) {
            imageProxy.close()
            return
        }
        if (frameTotal <= 10) {
            imageProxy.close()
            return
        }
        if (isOperatingPicture) {
            imageProxy.close()
            return
        }

        isOperatingPicture = true

        // convert to NV21 bytes then process (do heavy work off main)
        singleExecutor.execute {
            try {
                val nv21 = CameraXHelper.imageProxyToNv21(imageProxy)
                val bmp = nv21?.let { CameraXHelper.nv21ToBitmap(it, imageProxy.width, imageProxy.height) }
                if (nv21 == null || bmp == null) {
                    return@execute
                }

                // create detect dir name
                if (mDetectImgDirName == null) {
                    mDetectImgDirName = "${Date().time}${File.separator}"
                }

                // same logic as Java:
                if (photoIndex == 0) {
                    // detect id card type
                    val cardTypeObj = try {
                        // PaDetectorManager.getIdCardType(context, bitmap, previewSizeAfterResize, photoFrameVerticesAfterResize)
                        PaDetectorManager.getIdCardType(getApplication(), bmp, previewViewSizeAfterResize, photoFrameVerticesAfterResize)
                    } catch (e: Exception) {
                        onHint("getIdCardType error: ${e.message}")
                        null
                    }
                    val idType = cardTypeObj?.result ?: IdCardType.ERROR_INVALID_ID_CARD
                    idCardTypeResult = idType
                    updateUiByIdCardType(idType)

                    if (idCardTypeResult == IdCardType.ID_CARD_TYPE_18) {
                        // first quality check
                        val quality = checkQuality(bmp)
                        updateUiByQualityInspectionResult(quality, false)
                        if (quality == null || quality.result != QualityInspectionResult.SUCCESS) {
                            // wait next frame
                            return@execute
                        }
                        // save first corrected
                        correctedImage1 = quality.correctBitmap
                        imageDigest1 = quality.correctImageDigest
                        imageBase641 = quality.correctImageBase64

                        if (saveCorrectedImage) {
                            try {
                                CameraXHelper.saveBitmapToFile(getApplication<Application>().cacheDir, "${mDetectImgDirName}corrected_image_1.jpg", correctedImage1!!)
                            } catch (e: Exception) {
                                Log.e(TAG, "save corrected1 fail", e)
                            }
                        }

                        // open torch (flash)
                        openAutoFlash()
                        openLightTime = System.currentTimeMillis()
                        photoIndex++
                        onHint("开始闪光灯抓拍")
                    } else {
                        // not 18
                    }
                } else {
                    // during flash capture
                    val currentTime = System.currentTimeMillis()

                    if (normalFlashFilePaths.size >= LIGHT_CAPTURE_MAX_COUNT ||
                        (startCaptureTime != 0L && currentTime - startCaptureTime >= intervalFromStartCaptureToCloseLight)
                    ) {
                        closeAutoFlash()
                    }

                    if (normalFlashFilePaths.size >= LIGHT_CAPTURE_MAX_COUNT ||
                        (startCaptureTime != 0L && currentTime - startCaptureTime >= intervalFromStartCaptureToEndCapture)
                    ) {
                        // finished -> process
                        val msg = processAfterFlashCapture()
                        onHint(msg)
                        return@execute
                    }

                    if (currentTime - openLightTime >= intervalFromOpenLightToStartCapture) {
                        // quality check for light
                        lightCaptureProgressCount.incrementAndGet()
                        val qualityForLight = checkQualityForLight(nv21, imageProxy.width, imageProxy.height)
                        val passedTemp = (qualityForLight?.result == QualityInspectionResult.SUCCESS)
                        if (!qualityPassed && passedTemp) {
                            qualityPassed = true
                            startCaptureTime = currentTime
                            onHint("闪光灯抓图开始")
                        }

                        if (qualityPassed) {
                            val cut = qualityForLight?.cutOutBitmap
                            val score = qualityForLight?.score ?: 0f
                            if (cut != null && normalFlashFilePaths.size < LIGHT_CAPTURE_MAX_COUNT) {
                                val path = CameraXHelper.saveBitmapToFile(getApplication<Application>().cacheDir, "${mDetectImgDirName}${currentTime}_light_cut.jpg", cut)
                                normalFlashFilePaths.add(Pair(path, score))
                            }
                        }

                        lightCaptureProgressCount.decrementAndGet()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "onImageProxy process error", e)
            } finally {
                isOperatingPicture = false
                imageProxy.close()
            }
        }
    }

    // -------------------------------
    // helper methods similar to Java
    // -------------------------------
    private fun openAutoFlash() {
        // ViewModel doesn't control CameraX torch directly here.
        // The Screen must hold CameraControl and call cameraControl.enableTorch(true).
        // To keep parity, emit hint and expect Screen to enable torch via callback or expose a flow.
        onHint("请在 Screen 中打开闪光灯（CameraControl.enableTorch(true)）")
    }

    private fun closeAutoFlash() {
        onHint("请在 Screen 中关闭闪光灯（CameraControl.enableTorch(false)）")
    }

    private fun checkQuality(rawBitmap: Bitmap): QualityInspectionResult? {
        return try {
            val option = QualityInspectionOptionParams()
            option.needCheckLightSpot = true
            option.needCheckGray = true
            option.needControlAngle = false
            option.flashCheck = false
            option.needCheckWindow = true
            PaDetectorManager.do18QualityInspection(getApplication(), rawBitmap, previewViewSizeAfterResize, photoFrameVerticesAfterResize, option)
        } catch (e: Exception) {
            Log.e(TAG, "checkQuality fail", e)
            null
        }
    }

    private fun checkQualityForLight(nv21: ByteArray, width: Int, height: Int): QualityInspectionResult? {
        return try {
            PaDetectorManager.do18QualityInspectionForLight(
                getApplication(),
                nv21,
                width,
                height,
                0, // displayOrientation - may need adjust
                previewViewSizeAfterResize,
                photoFrameVerticesAfterResize,
                lightThresholds,
                !qualityPassed
            )
        } catch (e: Exception) {
            Log.e(TAG, "checkQualityForLight fail", e)
            null
        }
    }

    private fun processAfterFlashCapture(): String {
        if (normalFlashFilePaths.size < 2) {
            reset()
            return "合格照片 < 2，重新拍摄"
        }

        normalFlashEdgeResult.clear()
        for (i in 0 until normalFlashFilePaths.size) {
            val filePath = normalFlashFilePaths[i].first
            var bmp = BitmapFactory.decodeFile(filePath)
            val edgeResult = try {
                PaDetectorManager.detectEdgeNew(getApplication(), bmp)
            } catch (e: Exception) {
                null
            }
            if (edgeResult != null && edgeResult.outImg != null) {
                bmp = edgeResult.outImg
                try {
                    CameraXHelper.saveBitmapToFile(getApplication<Application>().cacheDir, File(filePath).name, bmp)
                } catch (e: Exception) {
                    Log.e(TAG, "overwrite edge file fail", e)
                }
            }
            normalFlashEdgeResult.add(edgeResult?.cardType ?: 0)
            val lightValue = try {
                PaDetectorManager.getLightLightValue(bmp)
            } catch (e: Exception) {
                0f
            }
            normalFlashFilePaths[i] = Pair(filePath, lightValue)
        }

        normalFlashFilePaths.sortBy { it.second }
        val size = normalFlashFilePaths.size
        val firstIndex = 0
        val secondIndex = size / 2

        if (normalFlashEdgeResult.getOrNull(firstIndex) != 0 || normalFlashEdgeResult.getOrNull(secondIndex) != 0) {
            reset()
            return "图片质量不合格，重新拍摄"
        }

        try {
            val flashFilePath1 = normalFlashFilePaths[firstIndex].first
            val flashFilePath2 = normalFlashFilePaths[secondIndex].first
            val flashBitmap1 = BitmapFactory.decodeFile(flashFilePath1)
            val flashBitmap2 = BitmapFactory.decodeFile(flashFilePath2)

            val flashResultFirst = PaDetectorManager.doQualityInspectionForLight(getApplication(), flashBitmap1)
            val flashResultSecond = PaDetectorManager.doQualityInspectionForLight(getApplication(), flashBitmap2)

            if (flashResultFirst.result == QualityInspectionResult.BLURRING || flashResultSecond.result == QualityInspectionResult.BLURRING) {
                reset()
                return "图片模糊，重新拍摄"
            } else {
                correctedImage2 = flashBitmap1
                imageDigest2 = flashResultFirst.correctImageDigest
                imageBase642 = flashResultFirst.correctImageBase64

                correctedImage3 = flashBitmap2
                imageDigest3 = flashResultSecond.correctImageDigest
                imageBase643 = flashResultSecond.correctImageBase64

                // identification & OCR
                doIdentificationAndOcrDetect(flashFilePath1, flashFilePath2)
                return "拍摄完成"
            }
        } catch (e: Exception) {
            Log.e(TAG, "processAfterFlashCapture error", e)
            reset()
            return "处理失败，重新拍摄"
        } finally {
            // cleanup temp files
            try {
                normalFlashFilePaths.forEach { (path, _) -> File(path).delete() }
            } catch (e: Exception) {
                Log.e(TAG, "cleanup fail", e)
            }
        }
    }

    private fun doIdentificationAndOcrDetect(flashFilePath1: String, flashFilePath2: String) {
        // vibrate (can't access vibrator here; Screen can do)
        onHint("开始鉴伪与 OCR")

        viewModelScope.launch(Dispatchers.IO) {
            val identification18Result: Identification18Result? = try {
                PaDetectorManager.do18Identification(getApplication(), arrayOf(correctedImage1, correctedImage2, correctedImage3))
            } catch (e: Exception) {
                Log.e(TAG, "do18Identification error", e)
                null
            }

            // release some bitmaps to save memory
            correctedImage1 = null

            completeShot(identification18Result)
        }
    }

    private fun completeShot(identificationResult: Identification18Result?) {
        needGetPreviewFrame = false
        val cardResult = CardResult()
        cardResult.cardType = 2
        cardResult.allPassed = identificationResult?.isSuccess ?: false
        val errCodes = identificationResult?.errorCodes
        cardResult.hologramPassed = (errCodes == null || !errCodes.contains(Identification18Result.NO_HOLOGRAM))

        // OCR
        ocr(cardResult)
    }

    private fun ocr(cardResult: CardResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val base64Images = arrayOf(imageBase641, imageBase643, imageBase641, imageBase641)
            val imageDigests = arrayOf(imageDigest1, imageDigest3, imageDigest1, imageDigest1)

            val requestBody = OcrRequestBody()
            // assume OcrRequestBody has method / fields similar to Java; adjust if needed
            requestBody.seeThroughFlag = "1"
            requestBody.file.images = base64Images
            requestBody.file.tokens = imageDigests

            try {
                val call = ApiCreater.create(getApplication()).ocr(requestBody)
                call.enqueue(object : retrofit2.Callback<OcrResponse> {
                    override fun onResponse(call: retrofit2.Call<OcrResponse>, response: retrofit2.Response<OcrResponse>) {
                        val ocrResponse = response.body()
                        if (ocrResponse != null && ocrResponse.data != null) {
                            EventBus.getDefault().postSticky(OcrEvent(true, idCardTypeResult, ocrResponse))
                        }
                        onFinished(cardResult)
                    }

                    override fun onFailure(call: retrofit2.Call<OcrResponse>, t: Throwable) {
                        EventBus.getDefault().postSticky(OcrEvent(true))
                        onFinished(cardResult)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "ocr request fail", e)
                onFinished(cardResult)
            }
        }
    }

    // UI hints mapping
    private fun updateUiByIdCardType(idCardType: Int) {
        when (idCardType) {
            IdCardType.ID_CARD_TYPE_03 -> onHint("检测到 03 版卡，当前仅支持 18 版")
            IdCardType.ID_CARD_TYPE_18 -> onHint("检测到 18 版身份证")
            IdCardType.ERROR_INVALID_ID_CARD -> onHint("未检测到身份证")
            else -> onHint("检测异常: $idCardType")
        }
    }

    private fun updateUiByQualityInspectionResult(result: QualityInspectionResult?, isCheckIdCardType: Boolean) {
        if (result == null) return
        when (result.result) {
            QualityInspectionResult.SDK_AUTH_ERROR -> onHint("SDK 授权异常")
            QualityInspectionResult.NO_CARD_DETECTED -> onHint("未检测到身份证")
            QualityInspectionResult.TOO_FAR_OUT_BORDER -> onHint("身份证超出边框")
            QualityInspectionResult.NOT_FIT_THE_BORDER -> onHint("身份证未对齐边框")
            QualityInspectionResult.TOO_FAR_IN_BORDER -> onHint("身份证离边框太远")
            QualityInspectionResult.TOO_DARK -> onHint("光线太暗")
            QualityInspectionResult.TOO_BRIGHT -> onHint("光线太亮")
            QualityInspectionResult.HAS_LIGHT_SPOT -> onHint("有光斑")
            QualityInspectionResult.IS_GRAY_COPY -> onHint("检测到复印件")
            QualityInspectionResult.BLURRING -> onHint("图像模糊")
            QualityInspectionResult.NO_WINDOW -> onHint("未检测到透明窗")
            QualityInspectionResult.SUCCESS -> {
                onHint("质量检测通过")
            }
            else -> {}
        }
    }
}
