package com.tonapps.tonkeeper.ui.base

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.tonapps.qr.QRImageAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import uikit.HapticHelper
import uikit.base.BaseFragment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalCamera2Interop::class)
abstract class QRCameraScreen(
    @LayoutRes layoutId: Int
): BaseFragment(layoutId) {

    data class FlashConfig(
        val isFlashAvailable: Boolean,
        val isFlashEnabled: Boolean,
    )

    private companion object {

        private fun createCameraController(
            context: Context,
        ): LifecycleCameraController {
            val cameraController = LifecycleCameraController(context)
            cameraController.isTapToFocusEnabled = true
            cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraController.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            cameraController.imageAnalysisOutputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888
            cameraController.cameraControl?.let { control ->
                control.setLinearZoom(0.1f)
                control.setExposureCompensationIndex(1)
            }
            return cameraController
        }
    }

    private val qrAnalyzer = QRImageAnalyzer()
    private val cameraController: LifecycleCameraController by lazy {
        createCameraController(requireContext())
    }

    val readerFlow: Flow<String> = qrAnalyzer.flow.map {
        it.rawValue
    }.filterNotNull().shareIn(lifecycleScope, SharingStarted.Lazily, 1)

    private val _flashConfigFlow = MutableStateFlow(FlashConfig(
        isFlashAvailable = false,
        isFlashEnabled = false
    ))

    val flashConfigFlow = _flashConfigFlow.asStateFlow()

    private val activityResultLauncher = registerForPermission { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        }
    }

    abstract var cameraView: PreviewView

    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private val lifecycleCallback = object : FragmentManager.FragmentLifecycleCallbacks() {

        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
            super.onFragmentViewCreated(fm, f, v, savedInstanceState)
            if (f == this@QRCameraScreen) {
                checkAndStartCamera()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.registerFragmentLifecycleCallbacks(lifecycleCallback, false)
    }

    private fun checkAndStartCamera() {
        if (hasPermission(android.Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            activityResultLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        cameraController.setImageAnalysisAnalyzer(cameraExecutor, qrAnalyzer)
        cameraController.torchState.observe(viewLifecycleOwner) { state ->
            _flashConfigFlow.update {
                it.copy(
                    isFlashAvailable = true,
                    isFlashEnabled = state == TorchState.ON
                )
            }
        }

        cameraView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        cameraView.controller = cameraController
        cameraController.bindToLifecycle(this)
    }

    fun toggleFlash() {
        val cameraController = cameraView.controller ?: return
        val flashMode = cameraController.torchState.value == TorchState.ON
        cameraController.enableTorch(!flashMode).addListener({
            HapticHelper.selection(requireContext())
        }, cameraExecutor)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.unbind()
        qrAnalyzer.release()
        cameraExecutor.shutdown()
        parentFragmentManager.unregisterFragmentLifecycleCallbacks(lifecycleCallback)
    }
}