package com.tonapps.blockchain.model.legacy

import com.tonapps.icu.Coins
import kotlin.math.abs

data class Fee(
    val value: Coins,
    val isRefund: Boolean,
    val token: TokenEntity = TokenEntity.TON
) {

    val fee: Coins
        get() = if (isRefund) Coins.Companion.ZERO else value

    val refund: Coins
        get() = if (isRefund) value else Coins.Companion.ZERO

    constructor(value: Long, isRefund: Boolean) : this(
        value = Coins.Companion.of(value),
        isRefund = isRefund,
    )

    constructor(value: Long) : this(
        value = abs(value),
        isRefund = value > 0
    )
}