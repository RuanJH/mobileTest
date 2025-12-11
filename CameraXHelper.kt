// CameraXHelper.kt
package com.your.package // 改为你的包名

import android.graphics.*
import android.media.Image
import android.media.ImageFormat
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

object CameraXHelper {
    private const val TAG = "CameraXHelper"

    /**
     * Convert ImageProxy (YUV_420_888) to NV21 byte array (Y V U interleaved)
     * This matches Camera1 NV21 format used in original Java code.
     */
    fun imageProxyToNv21(image: ImageProxy): ByteArray? {
        try {
            val format = image.format
            if (format != ImageFormat.YUV_420_888) {
                // fallback: try to get JPEG from planes
                val plane = image.planes[0]
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                return bytes
            }

            val width = image.width
            val height = image.height
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val ySize = yPlane.buffer.remaining()
            val uSize = uPlane.buffer.remaining()
            val vSize = vPlane.buffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // copy Y
            yPlane.buffer.get(nv21, 0, ySize)

            // Interleave VU
            val rowStrideU = uPlane.rowStride
            val pixelStrideU = uPlane.pixelStride
            val rowStrideV = vPlane.rowStride
            val pixelStrideV = vPlane.pixelStride

            val uBuffer = ByteArray(uSize)
            val vBuffer = ByteArray(vSize)
            uPlane.buffer.get(uBuffer)
            vPlane.buffer.get(vBuffer)

            var pos = ySize
            // Assumes subsampled chroma (width/2 * height/2)
            val chromaHeight = height / 2
            val chromaWidth = width / 2

            // generic interleave loop:
            var index = 0
            for (row in 0 until chromaHeight) {
                val baseU = row * rowStrideU
                val baseV = row * rowStrideV
                var col = 0
                while (col < chromaWidth) {
                    val uIndex = baseU + col * pixelStrideU
                    val vIndex = baseV + col * pixelStrideV
                    if (vIndex < vBuffer.size && uIndex < uBuffer.size && pos + 1 < nv21.size) {
                        nv21[pos++] = vBuffer[vIndex]
                        nv21[pos++] = uBuffer[uIndex]
                    } else {
                        // safety break
                        break
                    }
                    col++
                }
            }
            return nv21
        } catch (e: Exception) {
            Log.e(TAG, "imageProxyToNv21 failed", e)
            return null
        }
    }

    /**
     * Convert NV21 byte[] to Bitmap (via YuvImage -> JPEG -> decode)
     */
    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap? {
        return try {
            val yuv = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, width, height), 90, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "nv21ToBitmap fail", e)
            null
        }
    }

    /**
     * Save bitmap to file path, return absolute path
     */
    fun saveBitmapToFile(cacheDir: File, relativeName: String, bitmap: Bitmap): String {
        val dir = File(cacheDir, "detect_images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, relativeName)
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()
        } finally {
            fos?.close()
        }
        return file.absolutePath
    }

    /**
     * Save raw bytes to file (used if you have jpeg bytes)
     */
    fun saveBytesToFile(cacheDir: File, relativeName: String, bytes: ByteArray): String {
        val dir = File(cacheDir, "detect_images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, relativeName)
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
