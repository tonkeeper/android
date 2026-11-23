package com.tonapps.perps.data

import java.math.BigDecimal

private const val MAX_WIRE_DECIMAL_LENGTH = 64
private const val MAX_WIRE_DECIMAL_SCALE = 64

fun String?.toWireDecimalOrNull(): BigDecimal? {
    if (isNullOrBlank() || length > MAX_WIRE_DECIMAL_LENGTH) {
        return null
    }
    return toBigDecimalOrNull()?.takeIf { it.scale() in -MAX_WIRE_DECIMAL_SCALE..MAX_WIRE_DECIMAL_SCALE }
}

fun Double.toFiniteBigDecimalOrNull(): BigDecimal? = takeIf { it.isFinite() }?.let(BigDecimal::valueOf)
