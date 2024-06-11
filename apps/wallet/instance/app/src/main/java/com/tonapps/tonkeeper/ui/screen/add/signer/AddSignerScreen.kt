package com.tonapps.tonkeeper.ui.screen.add.signer

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatImageView
import androidx.camera.core.TorchState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.common.Barcode
import com.tonapps.qr.QRImageAnalyzer
import com.tonapps.tonkeeper.core.signer.SignerApp
import com.tonapps.tonkeeperx.R
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.widget.HeaderView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AddSignerScreen: BaseFragment(R.layout.fragment_signer_add), BaseFragment.BottomSheet {

    private val activityResultLauncher = registerForPermission { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        }
    }

    private val qrAnalyzer = QRImageAnalyzer()

    private var isFlashAvailable = false

    private lateinit var containerView: View
    private lateinit var headerView: HeaderView
    private lateinit var flashView: AppCompatImageView
    private lateinit var signerOpenButton: Button
    private lateinit var cameraView: PreviewView

    private lateinit var cameraExecutor: ExecutorService

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        containerView = view.findViewById(R.id.container)
        containerView.applyNavBottomPadding()

        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }
        headerView.background = null

        cameraView = view.findViewById(R.id.camera)

        flashView = view.findViewById(R.id.flash)

        signerOpenButton = view.findViewById(R.id.signer_open)
        signerOpenButton.setOnClickListener {
            SignerApp.openAppOrInstall(requireContext())
            finish()
        }

        collectFlow(qrAnalyzer.flow, ::handleBarcode)
        checkAndStartCamera()
    }

    private fun handleBarcode(barcode: Barcode) {
        val uri = barcode.rawValue?.let { Uri.parse(it) } ?: return
        if (uri.host != "signer") {
            return
        }

        /*val fragment = InitScreen.singer(uri) ?: return
        navigation?.add(fragment)
        finish()*/
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
        flashView.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        flashView.imageTintList = ColorStateList.valueOf(Color.BLACK)
    }

    private fun flashDisabled() {
        flashView.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#14ffffff"))
        flashView.imageTintList = ColorStateList.valueOf(Color.WHITE)
    }

    private fun checkAndStartCamera() {
        if (hasPermission(android.Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            activityResultLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        qrAnalyzer.release()
    }

    companion object {
        fun newInstance() = AddSignerScreen()
    }
}