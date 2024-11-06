package com.tonapps.tonkeeper.extensions

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.icu.Coins

fun Coins.toGrams(): org.ton.block.Coins {
    val value = toLong()
    if (0 > value) {
        val exception = IllegalArgumentException("Value must be positive!\n" +
                "BigDecimal: ${this.value}\n" +
                "decimals: ${this.decimals}\n" +
                "long: $value")

        FirebaseCrashlytics.getInstance().recordException(exception)
        throw exception
    }
    return org.ton.block.Coins.ofNano(value)
}