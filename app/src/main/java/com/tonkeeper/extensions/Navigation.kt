package com.tonkeeper.extensions

import com.tonkeeper.fragment.camera.CameraFragment
import uikit.navigation.Navigation

fun Navigation.openCamera() {
    add(CameraFragment.newInstance())
}