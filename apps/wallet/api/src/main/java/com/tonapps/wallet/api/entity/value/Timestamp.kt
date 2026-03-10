package com.tonapps.wallet.api.entity.value

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@JvmInline
@Parcelize
value class Timestamp(val value: Long) : Parcelable, Comparable<Timestamp> {

    data class Range(
        val from: Timestamp,
        val to: Timestamp
    )

    fun toLong() = value

    fun seconds() = value / 1000

    override fun compareTo(other: Timestamp): Int {
        return value.compareTo(other.value)
    }

    companion object {

        val zero = Timestamp(0)
        val now = Timestamp(System.currentTimeMillis())

        fun from(value: Long) = if (value < 1_000_000_000_000L) {
            Timestamp(value * 1000)
        } else Timestamp(value)
    }
}