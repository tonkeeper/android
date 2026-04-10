package com.tonapps.blockchain.model.legacy

import com.tonapps.icu.Coins

data class Amount(
    val value: Coins = Coins.Companion.ZERO,
    val token: TokenEntity = TokenEntity.TON
) {

    val isTon: Boolean
        get() = token.isTon

    val isTrx: Boolean
        get() = token.isTrx

    val symbol: String
        get() = token.symbol

    val decimals: Int
        get() = token.decimals
}