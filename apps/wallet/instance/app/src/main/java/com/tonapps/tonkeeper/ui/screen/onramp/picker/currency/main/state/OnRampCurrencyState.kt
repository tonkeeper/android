package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.state

import com.tonapps.wallet.data.core.currency.WalletCurrency

data class OnRampCurrencyState(
    val send: WalletCurrency,
    val receive: WalletCurrency,
)