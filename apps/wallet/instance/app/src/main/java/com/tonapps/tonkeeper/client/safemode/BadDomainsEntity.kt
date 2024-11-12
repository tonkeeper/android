package com.tonapps.tonkeeper.client.safemode

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BadDomainsEntity(
    val array: Array<String>
): Parcelable {

    val isEmpty: Boolean
        get() = array.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BadDomainsEntity

        return array.contentEquals(other.array)
    }

    override fun hashCode(): Int {
        return array.contentHashCode()
    }
}