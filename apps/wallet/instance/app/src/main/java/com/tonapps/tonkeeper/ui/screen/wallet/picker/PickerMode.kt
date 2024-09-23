package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class PickerMode: Parcelable {
    data object Default: PickerMode()
    data class Focus(val walletId: String): PickerMode()
    data class TonConnect(val walletId: String): PickerMode()
}