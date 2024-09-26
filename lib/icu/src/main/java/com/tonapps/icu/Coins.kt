package com.tonapps.icu

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

data class Coins(
    val value: BigDecimal,
    val decimals: Int = DEFAULT_DECIMALS,
): Parcelable, Comparable<Coins> {

    companion object {

        @JvmField
        val CREATOR = object : Parcelable.Creator<Coins> {
            override fun createFromParcel(parcel: Parcel) = Coins(parcel)
            override fun newArray(size: Int): Array<Coins?> = arrayOfNulls(size)
        }

        const val DEFAULT_DECIMALS = 9

        val ZERO = of(BigDecimal.ZERO, DEFAULT_DECIMALS)

        val ONE = of(BigDecimal.ONE, DEFAULT_DECIMALS)

        fun of(
            value: String,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            /*val divisor = BigDecimal.TEN.pow(decimals)
            val preparedValue = prepareValue(value)
            val bigDecimal = safeBigDecimal(preparedValue).divide(divisor, decimals, RoundingMode.FLOOR)
            return of(bigDecimal.toDouble(), decimals)*/
            val bigDecimal = safeBigDecimal2(value)
            return Coins(bigDecimal, decimals)
        }

        fun ofNano(
            value: String,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            if (value.length > 19) {
                return of(BigInteger(value), decimals)
            }
            val long = value.toLongOrNull() ?: return ZERO
            return of(long, decimals)
        }

        fun of(
            value: BigDecimal,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            return Coins(value, decimals)
        }

        fun of(
            value: BigInteger,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            val bigDecimal = BigDecimal(value)
            return Coins(bigDecimal, decimals)
        }

        fun of(
            value: Long,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            if (value == 0L) {
                return ZERO
            }
            val bigDecimal = safeBigDecimal(value).movePointLeft(decimals)
            return Coins(bigDecimal, decimals)
        }

        fun of(
            value: Double,
            decimals: Int = DEFAULT_DECIMALS,
        ): Coins {
            val bigDecimal = safeBigDecimal(value) //.movePointLeft(decimals)
            return Coins(bigDecimal, decimals)
        }

        private fun safeBigDecimal2(
            value: String
        ): BigDecimal {
            if (value.isBlank()) {
                return BigDecimal.ZERO
            }
            try {
                val input = prepareValue(value).filter { it.isDigit() or (it == '.') }
                return BigDecimal.ZERO.max(BigDecimal(input, MathContext.DECIMAL128))
            } catch (e: Throwable) {
                return BigDecimal.ZERO
            }
        }

        private fun safeBigDecimal(
            value: Any
        ): BigDecimal {
            return try {
                when (value) {
                    is BigInteger -> value.toBigDecimal()
                    is String -> BigDecimal(value)
                    is Double -> value.toBigDecimal()
                    is Long -> value.toBigDecimal()
                    else -> BigDecimal.ZERO
                }
            } catch (e: Throwable) {
                BigDecimal.ZERO
            }
        }

        fun safeParseDouble(value: String): Double {
            return prepareValue(value).toDoubleOrNull() ?: 0.0
        }

        @Deprecated("Remove this dirty hack")
        fun prepareValue(value: String): String {
            var v = value.trim()
            if (v.endsWith(".") || v.startsWith(",")) {
                v = v.dropLast(1)
            }
            if (v.startsWith("0")) {
                v = v.dropWhile { it == '0' }
            }
            if (v.startsWith(".") || v.startsWith(",")) {
                v = "0$v"
            }
            if (v.contains(",")) {
                v = v.replace(",", ".")
            }
            if (v.isEmpty()) {
                v = "0"
            }
            return v

        }

        inline fun <T> Iterable<T>.sumOf(selector: (T) -> Coins): Coins {
            var sum: Coins = ZERO
            for (element in this) {
                sum += selector(element)
            }
            return sum
        }
    }

    val isZero: Boolean
        get() = value == ZERO.value

    val isPositive: Boolean
        get() = value > ZERO.value

    val isNegative: Boolean
        get() = value < ZERO.value

    constructor(parcel: Parcel) : this(
        parcel.readSerializable() as BigDecimal,
        parcel.readInt(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(value)
        parcel.writeInt(decimals)
    }

    operator fun plus(other: Coins): Coins {
        return of(value + other.value, decimals)
    }

    operator fun minus(other: Coins) = of(value - other.value, decimals)

    operator fun times(other: Coins): Coins {
        //  = of(value * other.value, decimals)
        return of(value.multiply(other.value), decimals)
    }

    fun div(
        other: Coins,
        scale: Int = decimals,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Coins {
        try {
            val result = value.divide(other.value, scale, roundingMode)
            return of(result, scale)
        } catch (e: Throwable) {
            return ZERO
        }
    }

    operator fun div(other: Coins): Coins {
        return div(other)
    }

    operator fun rem(other: Coins) = of(value.remainder(other.value), decimals)

    operator fun inc() = Coins(value + ONE.value, decimals)

    operator fun dec() = Coins(value - ONE.value, decimals)

    fun multiply(other: Coins) = of(value.multiply(other.value), decimals)

    fun multiply(other: BigDecimal) = of(value.multiply(other), decimals)

    fun multiply(other: String) = of(value.multiply(BigDecimal(other)), decimals)

    fun divide(divisor: Coins, roundingMode: RoundingMode = RoundingMode.HALF_DOWN) = of(value.divide(divisor.value, roundingMode))

    fun divide(divisor: Int, roundingMode: RoundingMode = RoundingMode.HALF_DOWN) = of(value.divide(BigDecimal(divisor), roundingMode))

    override operator fun compareTo(other: Coins) = value.compareTo(other.value)

    fun abs() = Coins(value.abs(), decimals)

    fun stripTrailingZeros(): Coins = Coins(value.stripTrailingZeros(), decimals)

    fun toLong(): Long {
        val multiplier = BigDecimal.TEN.pow(decimals)
        val multipliedValue = value.multiply(multiplier)
        return multipliedValue.toLong()
    }

    fun diff(coins: Coins): Float {
        if (coins.isZero || isZero) {
            return 0f
        }
        val percentage = coins.value.divide(value, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)
        return percentage.toFloat()
    }

    fun setScale(scale: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): Coins {
        return of(value.setScale(scale, roundingMode), scale)
    }

    override fun describeContents(): Int {
        return 0
    }


}