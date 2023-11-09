package com.tonkeeper.fragment.camera

import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

object CameraHelper {

    fun getCameraSelector(
        cameraProvider: ProcessCameraProvider
    ): CameraSelector {
        val list = getAvailableCameraSelectors(cameraProvider)
        if (list.size == 1) {
            return list[0]
        }
        return list.find {
            it == CameraSelector.DEFAULT_BACK_CAMERA
        } ?: run {
            list[0]
        }
    }

    fun getAvailableCameraSelectors(
        cameraProvider: ProcessCameraProvider
    ): List<CameraSelector> {
        val list = mutableListOf<CameraSelector>()
        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            list.add(CameraSelector.DEFAULT_FRONT_CAMERA)
        }
        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            list.add(CameraSelector.DEFAULT_BACK_CAMERA)
        }
        return list
    }
}