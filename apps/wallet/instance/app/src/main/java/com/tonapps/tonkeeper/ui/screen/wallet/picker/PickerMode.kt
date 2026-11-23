package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.os.Parcelable
import androidx.annotation.StringRes
import com.tonapps.blockchain.model.legacy.WalletEntity
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class PickerMode: Parcelable {
    data object Default: PickerMode()
    data class Focus(val walletId: String): PickerMode()
    data class TonConnect(val walletId: String): PickerMode()
    data class Multichain(
        val openUrl: String,
        val perpsOnly: Boolean = false,
        @StringRes val subtitleResId: Int? = null,
    ): PickerMode() {

        fun accepts(wallet: WalletEntity): Boolean = if (perpsOnly) {
            wallet.supportsPerps
        } else {
            wallet.isMultichain
        }
    }
}