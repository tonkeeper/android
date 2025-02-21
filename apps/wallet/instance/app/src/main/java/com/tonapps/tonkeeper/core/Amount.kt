package com.tonapps.tonkeeper.core

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.TokenEntity

data class Amount(
    val value: Coins = Coins.ZERO,
    val token: TokenEntity = TokenEntity.TON
) {

    val isTon: Boolean
        get() = token.isTon

    val symbol: String
        get() = token.symbol

    val decimals: Int
        get() = token.decimals
}