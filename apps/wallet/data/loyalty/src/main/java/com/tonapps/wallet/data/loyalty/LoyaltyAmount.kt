package com.tonapps.wallet.data.loyalty

fun String.toPositiveOhmsOrNull(): String? {
    return toBigDecimalOrNull()
        ?.takeIf { it.signum() == 1 }
        ?.stripTrailingZeros()
        ?.toPlainString()
}
