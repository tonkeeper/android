package com.tonapps.tonkeeper.core.entities

import com.tonapps.icu.Coins

data class AmountEntity(
    val value: Value,
    val converted: Value
) {

    data class Value(
        val value: Coins,
        val format: CharSequence
    )
}