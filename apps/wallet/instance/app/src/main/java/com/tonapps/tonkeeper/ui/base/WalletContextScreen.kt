package com.tonapps.tonkeeper.ui.base

import androidx.annotation.LayoutRes
import com.tonapps.wallet.data.account.entities.WalletEntity

abstract class WalletContextScreen(
    @LayoutRes layoutId: Int,
    wallet: WalletEntity
): BaseWalletScreen<ScreenContext.Wallet>(layoutId, ScreenContext.Wallet(wallet)) {

    val wallet: WalletEntity
        get() = screenContext.wallet
}