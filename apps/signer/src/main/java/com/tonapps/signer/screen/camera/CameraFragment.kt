package com.tonapps.signer.screen.camera

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.camera.core.TorchState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.tonapps.signer.R
import com.tonapps.signer.deeplink.DeepLink
import com.tonapps.signer.password.Password
import com.tonapps.signer.screen.root.RootViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.widget.HeaderView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import qr.QRImageAnalyzer

class CameraFragment: BaseFragment(R.layout.fragment_camera), BaseFragment.BottomSheet {

    companion object {
        fun newInstance() = CameraFragment()
    }

    private val rootViewModel: RootViewModel by activityViewModel()

    private val activityResultLauncher = registerForPermission { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            finish()
        }
    }

    private var isFlashAvailable = false

    private val qrAnalyzer = QRImageAnalyzer()

    private lateinit var headerView: HeaderView
    private lateinit var cameraView: PreviewView
    private lateinit var flashView: AppCompatImageView

    private lateinit var cameraExecutor: ExecutorService

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.background = null
        headerView.doOnCloseClick = { finish() }

        cameraView = view.findViewById(R.id.camera)
        flashView = view.findViewById(R.id.flash)

        cameraExecutor = Executors.newSingleThreadExecutor()

        qrAnalyzer.flow.onEach(::handleBarcode).launchIn(lifecycleScope)
    }

    private fun handleBarcode(barcode: Barcode) {
        val data = barcode.rawValue ?: return
        if (DeepLink.isSupported(data)) {
            rootViewModel.processUri(data, true)
            finish()
        }
    }

    private fun startCamera() {
        val context = requireContext()
        val cameraController = LifecycleCameraController(context)
        cameraController.setImageAnalysisAnalyzer(mainExecutor, qrAnalyzer)

        cameraController.bindToLifecycle(this)
        cameraView.controller = cameraController

        applyFlash(cameraController)
    }

    private fun applyFlash(cameraController: CameraController) {
        cameraController.torchState.observe(viewLifecycleOwner) { state ->
            if (!isFlashAvailable) {
                isFlashAvailable = true
                flashView.visibility = View.VISIBLE
                return@observe
            }
            if (state == TorchState.ON) {
                flashEnabled()
            } else {
                flashDisabled()
            }

            HapticHelper.impactLight(requireContext())
        }

        flashView.setOnClickListener {
            val flashMode = cameraController.torchState.value == TorchState.ON
            cameraController.enableTorch(!flashMode)
        }
    }

    private fun flashEnabled() {
        setFlashColor(Color.WHITE, Color.BLACK)
    }

    private fun flashDisabled() {
        setFlashColor(Color.parseColor("#14ffffff"), Color.WHITE)
    }

    private fun setFlashColor(backgroundColor: Int, iconColor: Int) {
        flashView.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        flashView.imageTintList = ColorStateList.valueOf(iconColor)
    }

    private fun checkAndStartCamera() {
        if (hasPermission(android.Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            Password.setUnlock()
            activityResultLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        checkAndStartCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        qrAnalyzer.release()
    }
}