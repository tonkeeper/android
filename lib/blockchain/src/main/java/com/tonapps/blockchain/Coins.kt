package com.tonapps.blockchain

import android.os.Parcel
import android.os.Parcelable
import com.tonapps.extensions.readSerializableCompat
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

data class Coins(
    val value: BigDecimal
): Parcelable {

    companion object {

        @JvmField
        val CREATOR = object : Parcelable.Creator<Coins> {
            override fun createFromParcel(parcel: Parcel) = Coins(parcel)
            override fun newArray(size: Int): Array<Coins?> = arrayOfNulls(size)
        }

        val ZERO = Coins(BigDecimal.ZERO)

        const val DEFAULT_DECIMALS = 9
        // private const val BASE = 1000000000L

        fun of(
            value: String,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            val divisor = BigDecimal.TEN.pow(decimals)
            val bigDecimal = safeBigDecimal(prepareValue(value)).divide(divisor, decimals, RoundingMode.DOWN)
            return Coins(bigDecimal)
        }

        fun of(
            value: Long,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            val bigDecimal = BigDecimal(value / 10.0.pow(decimals))
            return Coins(bigDecimal)
        }

        fun of(
            value: Double,
            decimals: Int = DEFAULT_DECIMALS
        ): Coins {
            val bigDecimal = BigDecimal(value)
            return Coins(bigDecimal)
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
        get() = value.compareTo(BigDecimal.ZERO) == 0

    val isPositive: Boolean
        get() = value > BigDecimal.ZERO

    constructor(parcel: Parcel) : this(
        parcel.readSerializableCompat()!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(value)
    }

    operator fun plus(other: Coins) = Coins(value + other.value)

    operator fun minus(other: Coins) = Coins(value - other.value)

    operator fun times(other: Coins) = Coins(value * other.value)

    operator fun div(other: Coins) = Coins(value / other.value)

    operator fun rem(other: Coins) = Coins(value % other.value)

    operator fun inc() = Coins(value + BigDecimal.ONE)

    operator fun dec() = Coins(value - BigDecimal.ONE)

    operator fun compareTo(other: Coins) = value.compareTo(other.value)

    fun toTonLibCoin(): org.ton.block.Coins {
        return org.ton.block.Coins.ofNano(value.toBigInteger())
    }

    fun toDouble(): Double {
        return value.toDouble()
    }

    override fun describeContents(): Int {
        return 0
    }
}