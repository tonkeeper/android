package com.tonapps.tonkeeper.ui.base

import android.os.Parcelable
import com.tonapps.blockchain.model.legacy.WalletEntity
import kotlinx.parcelize.Parcelize

sealed class ScreenContext: Parcelable {

    @Parcelize
    data object None : ScreenContext()

    @Parcelize
    data object Ignore : ScreenContext()

    @Parcelize
    data class Wallet(val wallet: WalletEntity) : ScreenContext() {

        val isEmpty: Boolean
            get() = wallet.id.isBlank()
    }
}