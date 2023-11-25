package com.tonkeeper.fragment.camera

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.tonkeeper.MainActivity
import com.tonkeeper.R
import uikit.base.fragment.BaseFragment
import uikit.widget.HeaderView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment: BaseFragment(R.layout.fragment_camera), BaseFragment.BottomSheet {

    companion object {
        fun newInstance() = CameraFragment()
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {

        } else {
            finish()
        }
    }

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
        when (barcode.valueType) {
            Barcode.TYPE_URL -> {
                barcode.url?.url?.let { url ->
                    pushUri(Uri.parse(url))
                }
            }
        }
    }

    private fun pushUri(uri: Uri) {
        val activity = activity as? MainActivity ?: return
        activity.handleUri(uri)
        finish()
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