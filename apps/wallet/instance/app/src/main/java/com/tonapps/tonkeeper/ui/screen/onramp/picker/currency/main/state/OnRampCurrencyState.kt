package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.state

import com.tonapps.blockchain.model.legacy.WalletCurrency

data class OnRampCurrencyState(
    val send: WalletCurrency,
    val receive: WalletCurrency,
)