package com.tonapps.tonkeeper.extensions

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.icu.Coins
import java.math.BigInteger

fun Coins.toGrams(): org.ton.block.Coins {
    val value = toBigInteger()
    if (value < BigInteger.ZERO) {
        val exception = IllegalArgumentException("Value must be positive!\n" +
                "BigDecimal: ${this.value}\n" +
                "decimals: ${this.decimals}\n" +
                "long: $value")

        FirebaseCrashlytics.getInstance().recordException(exception)
        throw exception
    }
    return org.ton.block.Coins.ofNano(value)
}