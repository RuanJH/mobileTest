ssdRect = mapRatioToViewRect(
    ratio = ratio,
    imageWidth = mediaImage.width,
    imageHeight = mediaImage.height,
    previewWidth = previewView.width,
    previewHeight = previewView.height,
    rotationDegrees = rotationDegrees,
    scaleType = "FIT_START"
)

private fun mapRatioToViewRect(
    ratio: RectF,
    imageWidth: Int,
    imageHeight: Int,
    previewWidth: Int,
    previewHeight: Int,
    rotationDegrees: Int,
    scaleType: String = "FIT_START"
): Rect {
    // Step1: 如果有旋转，先交换宽高
    val (srcWidth, srcHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
        imageHeight to imageWidth
    } else {
        imageWidth to imageHeight
    }

    // Step2: FIT_START 用 max (保证画面能填满 View)
    val scale = max(
        previewWidth.toFloat() / srcWidth.toFloat(),
        previewHeight.toFloat() / srcHeight.toFloat()
    )

    val scaledWidth = srcWidth * scale
    val scaledHeight = srcHeight * scale

    // Step3: 偏移量 (FIT_START -> 左上角)
    val dx = 0f
    val dy = 0f

    // Step4: 先映射到原图坐标
    var rectF = RectF(
        ratio.left * imageWidth,
        ratio.top * imageHeight,
        ratio.right * imageWidth,
        ratio.bottom * imageHeight
    )

    // Step5: 如果有旋转，先把 rectF 转换到旋转后的坐标系
    if (rotationDegrees == 90) {
        rectF = RectF(
            srcWidth - rectF.bottom,
            rectF.left,
            srcWidth - rectF.top,
            rectF.right
        )
    } else if (rotationDegrees == 180) {
        rectF = RectF(
            srcWidth - rectF.right,
            srcHeight - rectF.bottom,
            srcWidth - rectF.left,
            srcHeight - rectF.top
        )
    } else if (rotationDegrees == 270) {
        rectF = RectF(
            rectF.top,
            srcHeight - rectF.right,
            rectF.bottom,
            srcHeight - rectF.left
        )
    }

    // Step6: 缩放 & 偏移
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
