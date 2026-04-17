package com.tonapps.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.tonapps.qr.QRImageAnalyzer
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ui.components.moon.MoonTopAppBar
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.theme.UIKit
import java.util.concurrent.Executors

// TODO refactor later
@Composable
fun ScannerScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val qrAnalyzer = remember { QRImageAnalyzer() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var flashEnabled by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            isTapToFocusEnabled = true
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisOutputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                qrAnalyzer.barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstOrNull()?.rawValue?.let(onResult)
                    }
            } catch (_: Throwable) {
            }
        }
    }

    // Collect QR scan results
    LaunchedEffect(qrAnalyzer) {
        qrAnalyzer.flow
            .map { it.rawValue }
            .filterNotNull()
            .collect { value ->
                withContext(Dispatchers.Main) {
                    onResult(value)
                }
            }
    }

    // Start camera when permission is granted
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.setImageAnalysisAnalyzer(cameraExecutor, qrAnalyzer)
            cameraController.torchState.observe(lifecycleOwner) { state ->
                flashAvailable = true
                flashEnabled = state == TorchState.ON
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.unbind()
            qrAnalyzer.release()
            cameraExecutor.shutdown()
        }
    }

    MoonSurface(Modifier.fillMaxSize(), color = Color.Black, shape = RectangleShape) {
        Box(
            Modifier.fillMaxSize()
        ) {
            // Camera preview
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            controller = cameraController
                            cameraController.bindToLifecycle(lifecycleOwner)
                            cameraController.cameraControl?.setLinearZoom(0.1f)
                            cameraController.cameraControl?.setExposureCompensationIndex(1)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            CameraOverlay(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(Localization.scan_qr_code),
                flashAvailable = flashAvailable,
                flashEnabled = flashEnabled,
                onFlashClick = { cameraController.enableTorch(!flashEnabled) },
            )

            // Top bar - close button
            val dimWhite = remember { Color.White.copy(alpha = 0.08f) } // TODO
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(dimWhite)
                    .clickable (onClick = { onClose() }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UIKitIcon.ic_chevron_left_16),
                    contentDescription = null,
                    tint = Color.White,
                )
            }

            // Bottom: gallery button
            Text(
                text = stringResource(Localization.select_from_gallery),
                style = UIKit.typography.label1,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF454545))
                    .clickable {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun CameraOverlay(
    modifier: Modifier = Modifier,
    title: String,
    flashAvailable: Boolean,
    flashEnabled: Boolean,
    onFlashClick: () -> Unit,
) {
    val dimColor = remember { Color(0xCC000000) }
    val dimWhite = remember { Color.White.copy(alpha = 0.08f) }
    val cornerRadiusDp = 20.dp
    val maxSizeDp = 350.dp
    val bracketLengthDp = 32.dp
    val strokeWidthDp = 4.dp

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val cutoutSize = minOf(size.width, size.height) - 64.dp.toPx()
            val finalSize = cutoutSize.coerceAtMost(maxSizeDp.toPx())
            val left = (size.width - finalSize) / 2f
            val top = (size.height - finalSize) / 2f
            val cornerRadius = cornerRadiusDp.toPx()
            val bracketLength = bracketLengthDp.toPx()

            drawRect(dimColor)

            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(finalSize, finalSize),
                cornerRadius = CornerRadius(cornerRadius),
                blendMode = BlendMode.Clear,
            )

            val path = Path()
            path.moveTo(left, top + bracketLength)
            path.lineTo(left, top)
            path.lineTo(left + bracketLength, top)

            path.moveTo(left + finalSize - bracketLength, top)
            path.lineTo(left + finalSize, top)
            path.lineTo(left + finalSize, top + bracketLength)

            path.moveTo(left, top + finalSize - bracketLength)
            path.lineTo(left, top + finalSize)
            path.lineTo(left + bracketLength, top + finalSize)

            path.moveTo(left + finalSize - bracketLength, top + finalSize)
            path.lineTo(left + finalSize, top + finalSize)
            path.lineTo(left + finalSize, top + finalSize - bracketLength)

            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(
                    width = strokeWidthDp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Miter,
                    pathEffect = PathEffect.cornerPathEffect(cornerRadius),
                ),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                style = UIKit.typography.h2,
                color = Color.White,
            )

            Spacer(modifier = Modifier.size(maxSizeDp))

            if (flashAvailable) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (flashEnabled) Color.White else dimWhite)
                        .clickable(onClick = onFlashClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(UIKitIcon.ic_flash),
                        contentDescription = null,
                        tint = if (flashEnabled) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
