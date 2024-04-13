package com.tonapps.blockchain

import java.math.BigDecimal
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
        val doubleValue = floatValue * 10.0.pow(-decimals.toDouble())
        val parsed = doubleValue.toFloat()
        val string = parsed.toString()
        if (string.endsWith("E-$decimals")) {
            return string.removeSuffix("E-$decimals").toFloat()
        }
        return parsed
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