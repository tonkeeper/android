package com.tonapps.wallet.data.token.entities

import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.WalletCurrency

data class TokenRateEntity(
    val currency: WalletCurrency,
    val fiat: Coins,
    val rate: Coins,
    val rateDiff24h: String,
) {

    companion object {
        val EMPTY = TokenRateEntity(WalletCurrency.TON, Coins.ZERO, Coins.ZERO, "")
    }
}