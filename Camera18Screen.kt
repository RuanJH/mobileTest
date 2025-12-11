// Camera18Screen.kt
package com.your.package // 改为你的包名

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Point
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Camera18Screen - Compose screen
 *
 * 参数:
 *  - onFinished: (CardResult) -> Unit
 *
 * 使用时创建 ViewModelProvider.Factory 来构造 Camera18ViewModel，或在这里使用 viewModel() 并传入 factories.
 *
 * 为简化，这里使用 AndroidManifest 已声明 CAMERA，且在首次展示时请求权限。
 */

@Composable
fun Camera18Screen(
    modifier: Modifier = Modifier.fillMaxSize(),
    onFinished: (com.pa.cardcheck.bean.CardResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // permission
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = remember {
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasCameraPermission = granted }
        )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // CameraX objects
    val previewView = remember { PreviewView(context) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    // ViewModel: we need a custom factory to pass callbacks to androidViewModel; for brevity use viewModel<> default and then manually set callbacks
    val vm: Camera18ViewModel = viewModel(
        factory = Camera18ViewModelFactory(
            context.applicationContext as android.app.Application,
            onFinished,
            { hint -> coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, hint, Toast.LENGTH_SHORT).show() } }
        )
    )

    // start camera when previewView has size
    AndroidView(factory = {
        previewView.apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }, modifier = modifier) { view ->
        view.post {
            val w = view.width
            val h = view.height
            if (w > 0 && h > 0) {
                // compute vertices using same dp sizes as Java
                val density = context.resources.displayMetrics.density
                val isLandscape = context.resources.configuration.screenWidthDp > context.resources.configuration.screenHeightDp
                val vertices = getPhotoFrameVertices(Point(w, h), isLandscape, density)
                vm.startDetection(Point(w, h), vertices, density, isLandscape)

                // start CameraX once
                if (cameraProvider == null) {
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        cameraProvider = provider
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                            // dispatch imageProxy to ViewModel for processing (ViewModel will close imageProxy after conversion)
                            vm.onImageProxy(imageProxy)
                        }

                        try {
                            provider.unbindAll()
                            val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                            cameraControl = camera.cameraControl
                        } catch (e: Exception) {
                            Log.e("Camera18Screen", "bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        }
    }

    // overlay & controls
    Box(modifier = Modifier.fillMaxSize()) {
        // draw frame based on ViewModel's vertices
        // For simplicity we use the vertices stored in VM (private), but VM doesn't expose them; we can recompute here
        // Let's compute visual frame at center using dp sizes similar to Java
        val cfg = context.resources.configuration
        val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
        val density = context.resources.displayMetrics.density
        // use PreviewView size
        val width = previewView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val height = previewView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val displayVertices = getPhotoFrameVertices(Point(width, height), isLandscape, density)

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (displayVertices.size == 4) {
                val left = displayVertices.minOf { it.x }.toFloat()
                val top = displayVertices.minOf { it.y }.toFloat()
                val right = displayVertices.maxOf { it.x }.toFloat()
                val bottom = displayVertices.maxOf { it.y }.toFloat()

                drawRect(color = Color(0x99000000.toInt()))
                drawRect(color = Color.Transparent, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(right - left, bottom - top))
                drawRect(color = Color.Green, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(right - left, bottom - top), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
            }
        }

        // controls
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.padding(12.dp)) {
                Button(onClick = {
                    // toggle torch
                    cameraControl?.enableTorch(true)
                }, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.Default.FlashOn, contentDescription = "flash")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("闪光灯开")
                }
                Button(onClick = {
                    vm.reset()
                    cameraControl?.enableTorch(false)
                }, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "reset")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重拍")
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * small helper to compute vertices same as Java getPhotoFrameVertices
 */
fun getPhotoFrameVertices(previewViewSize: Point, isScreenLandscape: Boolean, density: Float): Array<Point> {
    val SCREEN_RECT_WIDTH_FINAL_PORTRAIT = 340
    val SCREEN_RECT_WIDTH_FINAL_LANDSCAPE = 430
    val SCREEN_RECT_HEIGHT_FINAL_PORTRAIT = 215
    val SCREEN_RECT_HEIGHT_FINAL_LANDSCAPE = 272

    val photoFrameWidthTop = if (isScreenLandscape) SCREEN_RECT_WIDTH_FINAL_LANDSCAPE else SCREEN_RECT_WIDTH_FINAL_PORTRAIT
    val photoFrameHeight = if (isScreenLandscape) SCREEN_RECT_HEIGHT_FINAL_LANDSCAPE else SCREEN_RECT_HEIGHT_FINAL_PORTRAIT

    val frameWidthPx = (photoFrameWidthTop * density).toInt()
    val frameHeightPx = (photoFrameHeight * density).toInt()

    val previewWidth = previewViewSize.x
    val previewHeight = if (isScreenLandscape) previewViewSize.y else previewViewSize.y * 9 / 10

    val x1 = previewWidth / 2 - frameWidthPx / 2
    val y1 = previewHeight / 2 - frameHeightPx / 2
    val x2 = previewWidth / 2 + frameWidthPx / 2
    val y2 = y1
    val x3 = x2
    val y3 = previewHeight / 2 + frameHeightPx / 2
    val x4 = x1
    val y4 = y3

    return arrayOf(Point(x1, y1), Point(x2, y2), Point(x3, y3), Point(x4, y4))
}

/**
 * ViewModel factory to pass callbacks
 */
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class Camera18ViewModelFactory(
    private val application: Application,
    private val onFinished: (com.pa.cardcheck.bean.CardResult) -> Unit,
    private val onHint: (String) -> Unit
) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(Camera18ViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return Camera18ViewModel(application, onFinished = onFinished as (com.pa.cardcheck.bean.CardResult) -> Unit, onHint = onHint) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
