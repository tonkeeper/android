package com.tonapps.blockchain.model.legacy.errors

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.icu.Coins

class InsufficientFundsException(
    val currency: WalletCurrency,
    val required: Coins,
    val available: Coins,
    val type: InsufficientBalanceType,
    val withRechargeBattery: Boolean,
    val singleWallet: Boolean
) : Exception("Insufficient funds: required $required, available $available, currency $currency") {

}