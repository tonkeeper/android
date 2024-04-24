package com.tonapps.blockchain

import android.util.Log
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

object Coin {

    const val TON_DECIMALS = 9

    private const val DEFAULT_DECIMALS = 18
    private const val BASE = 1000000000L

    fun parseJettonBalance(
        v: String,
        decimals: Int
    ): Float {
        val bigDecimal = safeBigDecimal(v)
        val divisor = BigDecimal.TEN.pow(decimals)
        val result = bigDecimal.divide(divisor, decimals, RoundingMode.HALF_DOWN)
        return result.toFloat()
    }

    fun parseFloat(
        value: String,
        decimals: Int = TON_DECIMALS
    ): Float {
        val floatValue = value.toFloatOrNull() ?: return 0f
        val formatString = "%.${decimals}f"
        val formattedString = formatString.format(floatValue)
        return formattedString.toFloat()
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

    private fun safeBigDecimal(
        value: String
    ): BigDecimal {
        return try {
            BigDecimal(value)
        } catch (e: Throwable) {
            BigDecimal.ZERO
        }
    }

    fun toNano(value: Float): Long {
        return (value * BASE).toLong()
    }

    fun toCoins(value: Long): Float {
        return value / BASE.toFloat()
    }

}