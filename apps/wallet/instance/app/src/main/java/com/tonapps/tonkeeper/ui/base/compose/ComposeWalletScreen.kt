package com.tonapps.tonkeeper.ui.base.compose

import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.wallet.data.account.entities.WalletEntity

abstract class ComposeWalletScreen(wallet: WalletEntity): ComposeScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)) {

    val wallet: WalletEntity
        get() = (screenContext as ScreenContext.Wallet).wallet
}