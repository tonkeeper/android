package com.tonapps.tonkeeper.fragment.camera

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.camera.core.TorchState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.deeplink.DeepLink
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ton.block.AddrStd
import com.tonapps.qr.QRImageAnalyzer
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.widget.HeaderView
import uikit.widget.ModalView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    private val qrAnalyzer = QRImageAnalyzer()

    private var readyUrl = false
    private var isFlashAvailable = false

    private lateinit var headerView: HeaderView
    private lateinit var flashView: AppCompatImageView
    private lateinit var cameraView: PreviewView

    private lateinit var cameraExecutor: ExecutorService

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.background = null
        headerView.doOnCloseClick = { finish() }

        flashView = view.findViewById(R.id.flash)

        cameraView = view.findViewById(R.id.camera)

        cameraExecutor = Executors.newSingleThreadExecutor()

        qrAnalyzer.flow.onEach(::handleBarcode).launchIn(lifecycleScope)

        checkAndStartCamera()
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

    private fun handleBarcode(barcode: Barcode) {
        if (readyUrl) {
            return
        }
        val url = getUrlFromBarcode(barcode) ?: return
        readyUrl = true
        if (rootViewModel.processDeepLink(Uri.parse(url), true)) {
            finish()
        }
    }

    private fun getUrlFromBarcode(barcode: Barcode): String? {
        val rawValue = barcode.rawValue
        var url: String? = null
        if (DeepLink.isSupportedScheme(rawValue)) {
            url = rawValue
        } else if (barcode.valueType == Barcode.TYPE_TEXT) {
            url = createTransferLink(rawValue)
        } else if (barcode.valueType == Barcode.TYPE_URL) {
            url = barcode.url?.url
        }
        return url
    }

    private fun createTransferLink(value: String?): String? {
        if (value == null) {
            return null
        }
        return try {
            val address = AddrStd.parse(value).toString(userFriendly = true)
            "ton://transfer/$address"
        } catch (e: Exception) {
            null
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
}