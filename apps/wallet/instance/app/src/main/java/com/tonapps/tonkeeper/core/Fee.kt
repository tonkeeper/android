package com.tonapps.tonkeeper.core

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.TokenEntity
import kotlin.math.abs

data class Fee(
    val value: Coins,
    val isRefund: Boolean,
    val token: TokenEntity = TokenEntity.TON
) {

    val fee: Coins
        get() = if (isRefund) Coins.ZERO else value

    val refund: Coins
        get() = if (isRefund) value else Coins.ZERO

    constructor(value: Long, isRefund: Boolean) : this(
        value = Coins.of(value),
        isRefund = isRefund,
    )

    constructor(value: Long) : this(
        value = abs(value),
        isRefund = value > 0
    )
}