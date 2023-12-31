package com.tonkeeper.fragment.camera

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.tonapps.tonkeeperx.R
import com.tonkeeper.fragment.root.RootActivity
import org.ton.block.AddrStd
import uikit.base.BaseFragment
import uikit.widget.HeaderView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment: BaseFragment(R.layout.fragment_camera), BaseFragment.BottomSheet {

    companion object {

        fun newInstance() = CameraFragment()
    }

    private val activityResultLauncher = registerForPermission { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            finish()
        }
    }

    private var readyUrl = false

    private lateinit var cameraView: PreviewView
    private lateinit var headerView: HeaderView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraView = view.findViewById(R.id.camera)

        headerView = view.findViewById(R.id.header)
        headerView.background = null
        headerView.doOnCloseClick = { finish() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    private fun startCamera() {
        val context = requireContext()
        val cameraController = LifecycleCameraController(context)
        cameraController.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(
                listOf(barcodeScanner),
                CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor
            ) { result: MlKitAnalyzer.Result? ->
                val barcodeResults = result?.getValue(barcodeScanner)
                if ((barcodeResults == null) ||
                    (barcodeResults.size == 0) ||
                    (barcodeResults.first() == null)
                ) {
                    cameraView.overlay.clear()
                    cameraView.setOnTouchListener { _, _ -> false }
                    return@MlKitAnalyzer
                }

                handleBarcode(barcodeResults[0])
                cameraView.overlay.clear()
            }
        )

        cameraController.bindToLifecycle(this)
        cameraView.controller = cameraController
    }

    private fun checkAndStartCamera() {
        if (hasPermission(android.Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            activityResultLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun handleBarcode(barcode: Barcode) {
        val activity = activity ?: return

        if (readyUrl || activity !is RootActivity) {
            return
        }

        val url = getUrlFromBarcode(barcode) ?: return
        readyUrl = true

        activity.handleUri(Uri.parse(url))

        finish()
    }

    private fun getUrlFromBarcode(barcode: Barcode): String? {
        val rawValue = barcode.rawValue
        var url: String? = null
        if (rawValue?.startsWith("ton://transfer/") == true) {
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

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        checkAndStartCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }
}