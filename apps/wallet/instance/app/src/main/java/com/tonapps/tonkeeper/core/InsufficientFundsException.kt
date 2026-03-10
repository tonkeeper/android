package com.tonapps.tonkeeper.core

import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.ui.screen.send.main.helper.InsufficientBalanceType
import com.tonapps.wallet.data.core.currency.WalletCurrency

class InsufficientFundsException(
    val currency: WalletCurrency,
    val required: Coins,
    val available: Coins,
    val type: InsufficientBalanceType,
    val withRechargeBattery: Boolean,
    val singleWallet: Boolean
) : Exception("Insufficient funds: required $required, available $available, currency $currency") {

}