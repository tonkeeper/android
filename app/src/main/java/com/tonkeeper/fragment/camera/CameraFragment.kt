package com.tonkeeper.fragment.camera

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.tonkeeper.R
import uikit.base.fragment.BaseFragment
import uikit.widget.HeaderView

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

    private var cameraProvider: ProcessCameraProvider? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraView = view.findViewById(R.id.camera)

        headerView = view.findViewById(R.id.header)
        headerView.background = null
        headerView.doOnCloseClick = { finish() }
    }

    private fun startCamera() {
        val context = requireContext()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelectorUseCase = CameraHelper.getCameraSelector(cameraProvider!!)
                val previewUseCase = Preview.Builder().build().apply {
                    setSurfaceProvider(cameraView.surfaceProvider)
                }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelectorUseCase, previewUseCase)
                Log.d("CameraFragmentLog", "bindToLifecycle")
            } catch (e: Throwable) {
                Log.e("CameraFragmentLog", "startCamera error", e)
                finish()
            }
        }, context.mainExecutor)
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission(android.Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            activityResultLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
    }
}