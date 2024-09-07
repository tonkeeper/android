package com.tonapps.tonkeeper.ui.base

import android.os.Parcelable
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.parcelize.Parcelize

sealed class ScreenContext: Parcelable {

    @Parcelize
    data object None : ScreenContext()

    @Parcelize
    data class Wallet(val wallet: WalletEntity) : ScreenContext()
}