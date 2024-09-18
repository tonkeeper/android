package com.tonapps.tonkeeper.ui.base

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.tonapps.qr.QRImageAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import uikit.base.BaseFragment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

abstract class QRCameraScreen(
    @LayoutRes layoutId: Int
): BaseFragment(layoutId) {

    private val qrAnalyzer = QRImageAnalyzer()

    val readerFlow: Flow<String> = qrAnalyzer.flow.map {
        it.rawValue
    }.filterNotNull().shareIn(lifecycleScope, SharingStarted.Lazily, 1)

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
        val context = requireContext()
        val cameraController = LifecycleCameraController(context)
        cameraController.setImageAnalysisAnalyzer(mainExecutor, qrAnalyzer)

        cameraController.bindToLifecycle(this)
        cameraView.controller = cameraController
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        qrAnalyzer.release()
        parentFragmentManager.unregisterFragmentLifecycleCallbacks(lifecycleCallback)
    }
}