package com.tonapps.tonkeeper.ui.screen.camera

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class CameraMode: Parcelable {
    data object Default: CameraMode()
    data object Address: CameraMode()
}