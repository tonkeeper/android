package com.tonapps.tonkeeper.ui.base.compose

import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.blockchain.model.legacy.WalletEntity

abstract class ComposeWalletScreen(wallet: WalletEntity): ComposeScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)) {

    val wallet: WalletEntity
        get() = (screenContext as ScreenContext.Wallet).wallet
}