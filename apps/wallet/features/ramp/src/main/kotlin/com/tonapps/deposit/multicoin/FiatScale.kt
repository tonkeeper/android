package com.tonapps.deposit.multicoin

import java.util.Currency

private const val DEFAULT_FIAT_SCALE = 2

internal fun fiatScale(code: String): Int {
    return try {
        Currency.getInstance(code).defaultFractionDigits.takeIf { it >= 0 } ?: DEFAULT_FIAT_SCALE
    } catch (_: IllegalArgumentException) {
        DEFAULT_FIAT_SCALE
    }
}
