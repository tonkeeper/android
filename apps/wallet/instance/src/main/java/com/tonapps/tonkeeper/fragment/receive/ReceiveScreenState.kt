package com.tonapps.tonkeeper.fragment.receive

import android.graphics.Bitmap
import uikit.mvi.UiState

data class ReceiveScreenState(
    val qrCode: Bitmap? = null,
    val address: String? = null
): UiState()