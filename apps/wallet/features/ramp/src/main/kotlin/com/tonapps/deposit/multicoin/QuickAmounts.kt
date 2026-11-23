package com.tonapps.deposit.multicoin

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

private val DEFAULT_QUICK_AMOUNTS = listOf(50L, 100L, 500L)
private val QUICK_AMOUNT_MULTIPLIERS = listOf(2.5, 5.0, 25.0)
private val NICE_STEPS = listOf(1.0, 2.0, 5.0, 10.0)

// As on iOS: there is no rate to the dollar here, so the defaults, which suit a $20 minimum, are scaled
// by the minimum of the selected currency and kept round and growing.
internal fun quickAmounts(minimum: BigDecimal?): List<Long> {
    val value = minimum?.takeIf { it.signum() > 0 }?.toDouble() ?: return DEFAULT_QUICK_AMOUNTS
    val result = mutableListOf<Long>()
    for (multiplier in QUICK_AMOUNT_MULTIPLIERS) {
        var amount = niceAmount(value * multiplier)
        while (result.isNotEmpty() && amount <= result.last()) {
            amount = nextNiceAmount(amount)
        }
        result += amount
    }
    return result
}

private fun niceAmount(value: Double): Long {
    val exponent = floor(log10(value))
    val base = value / 10.0.pow(exponent)
    val step = NICE_STEPS.minBy { abs(ln(it / base)) }
    return max(1L, (step * 10.0.pow(exponent)).roundToLong())
}

private fun nextNiceAmount(amount: Long): Long {
    val exponent = floor(log10(amount.toDouble()))
    val base = amount / 10.0.pow(exponent)
    val next = NICE_STEPS.firstOrNull { it > base + 0.001 } ?: 10.0
    return (next * 10.0.pow(exponent)).roundToLong()
}