imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
    val mediaImage = imageProxy.image
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    if (mediaImage != null) {
        val yuvBytes = imageProxyToNV21(imageProxy)
        cardResult = sdkViewModel.OcrDetection(
            context = context,
            targetCardType = 3,
            cardRect = RectF(cardRect.value),
            bytes = yuvBytes,
            imageWidth = mediaImage.width,
            imageHeight = mediaImage.height,
            sensorOrientationDegrees = rotationDegrees,
            size = android.util.Size(previewView.width, previewView.height)
        )
        hintText = sdkViewModel.getOcrHintMsg(
            context = context,
            code = cardResult?.code ?: -1
        )
        if (cardResult?.code == ResultCode.SUCCESS) {
            provider.unbindAll()
            imageAnalyzer.clearAnalyzer()
            ocrViewModel.ocrSuccess()
        } else {
            cardResult?.ssdRectRatio?.let { ratio ->
                ssdRect = mapRatioToViewRect(
                    ratio = ratio,
                    imageWidth = mediaImage.width,
                    imageHeight = mediaImage.height,
                    previewWidth = previewView.width,
                    previewHeight = previewView.height,
                    scaleType = "FIT_START"
                )
            }
        }
        imageProxy.close()
    } else {
        imageProxy.close()
    }
}

private fun mapRatioToViewRect(
    ratio: RectF,
    imageWidth: Int,
    imageHeight: Int,
    previewWidth: Int,
    previewHeight: Int,
    scaleType: String = "FIT_START"
): Rect {
    // Step1: 计算缩放比例（保持宽高比）
    val scale = min(
        previewWidth.toFloat() / imageWidth.toFloat(),
        previewHeight.toFloat() / imageHeight.toFloat()
    )

    val scaledWidth = imageWidth * scale
    val scaledHeight = imageHeight * scale

    // Step2: 偏移量 (FIT_START -> 左上角对齐)
    val dx = if (scaleType == "FIT_START") 0f else (previewWidth - scaledWidth) / 2f
    val dy = if (scaleType == "FIT_START") 0f else (previewHeight - scaledHeight) / 2f

    // Step3: 先映射到原图尺寸，再缩放 & 偏移
    val rectF = RectF(
        ratio.left * imageWidth,
        ratio.top * imageHeight,
        ratio.right * imageWidth,
        ratio.bottom * imageHeight
    )

    rectF.left = rectF.left * scale + dx
    rectF.top = rectF.top * scale + dy
    rectF.right = rectF.right * scale + dx
    rectF.bottom = rectF.bottom * scale + dy

    return Rect(
        rectF.left.toInt(),
        rectF.top.toInt(),
        rectF.right.toInt(),
        rectF.bottom.toInt()
    )
}
