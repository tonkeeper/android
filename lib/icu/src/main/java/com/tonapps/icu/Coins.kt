package com.tonapps.icu

import android.os.Parcel
import android.os.Parcelable
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

data class Coins(
    val value: Double,
    val decimals: Int = DEFAULT_DECIMALS,
): Parcelable, Comparable<Coins> {

    companion object {

        @JvmField
        val CREATOR = object : Parcelable.Creator<Coins> {
            override fun createFromParcel(parcel: Parcel) = Coins(parcel)
            override fun newArray(size: Int): Array<Coins?> = arrayOfNulls(size)
        }

        const val DEFAULT_DECIMALS = 9

        val ZERO = Coins(0.0, DEFAULT_DECIMALS)

        val ONE = Coins(1.0, DEFAULT_DECIMALS)

        // private const val BASE = 1000000000L

        fun of(
            value: String,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            val divisor = BigDecimal.TEN.pow(decimals)
            val bigDecimal = safeBigDecimal(prepareValue(value)).divide(divisor, decimals, RoundingMode.FLOOR)
            return Coins(bigDecimal.toDouble(), decimals)
        }

        fun of(
            value: BigDecimal,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            return Coins(value.toDouble(), decimals)
        }

        fun of(
            value: Long,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            val bigDecimal = BigDecimal(value / 10.0.pow(decimals))
            return Coins(bigDecimal.toDouble(), decimals)
        }

        fun of(
            value: Double,
            decimals: Int = DEFAULT_DECIMALS,
        ): Coins {
            val bigDecimal = BigDecimal(value)
            return Coins(bigDecimal.toDouble(), decimals)
        }

        fun safeBigDecimal(
            value: Any
        ): BigDecimal {
            return try {
                when (value) {
                    is String -> BigDecimal(value)
                    is Double -> BigDecimal(value)
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
    }

    val isZero: Boolean
        get() = value == ZERO.value

    val isPositive: Boolean
        get() = value > ZERO.value

    constructor(parcel: Parcel) : this(
        parcel.readDouble(),
        parcel.readInt(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(value)
        parcel.writeInt(decimals)
    }

    operator fun plus(other: Coins) = Coins(value + other.value, decimals)

    operator fun minus(other: Coins) = Coins(value - other.value, decimals)

    operator fun times(other: Coins) = Coins(value * other.value, decimals)

    operator fun div(other: Coins) = Coins(value / other.value, decimals)

    operator fun rem(other: Coins) = Coins(value % other.value, decimals)

    operator fun inc() = Coins(value + ONE.value, decimals)

    operator fun dec() = Coins(value - ONE.value, decimals)

    override operator fun compareTo(other: Coins) = value.compareTo(other.value)

    fun abs() = Coins(kotlin.math.abs(value), decimals)

    fun stripTrailingZeros(): BigDecimal {
        return try {
            BigDecimal(value).stripTrailingZeros()
        } catch (e: Throwable) {
            BigDecimal.ZERO
        }
    }

    fun nano(): Long {
        return (value * 10.0.pow(decimals)).toLong()
    }

    override fun describeContents(): Int {
        return 0
    }


}