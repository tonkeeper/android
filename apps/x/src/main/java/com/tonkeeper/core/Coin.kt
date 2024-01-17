package com.tonkeeper.core

import android.icu.math.BigDecimal
import kotlin.math.pow

object Coin {

    const val TON_DECIMALS = 9

    private const val DEFAULT_DECIMALS = 18
    private const val BASE = 1000000000L

    fun parseFloat(
        value: String,
        decimals: Int = DEFAULT_DECIMALS
    ): Float {
        val floatValue = value.toFloatOrNull() ?: return 0f
        return floatValue * 10.0.pow(-decimals.toDouble()).toFloat()
    }

    fun bigDecimal(
        value: String,
        decimals: Int = TON_DECIMALS
    ): BigDecimal {
        return try {
            BigDecimal(value).movePointRight(decimals)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    fun bigDecimal(
        value: Long
    ): BigDecimal {
        return BigDecimal(value)
    }

    fun toNano(value: Float): Long {
        return (value * BASE).toLong()
    }

    fun toCoins(value: Long): Float {
        return value / BASE.toFloat()
    }

}