package com.tonapps.wallet.data.core

import android.os.Parcel
import android.os.Parcelable

@JvmInline
value class Trust(val type: String): Parcelable {

    constructor(parcel: Parcel) : this(parcel.readString()!!)

    companion object {
        val whitelist = Trust("whitelist")
        val graylist = Trust("graylist")
        val blacklist = Trust("blacklist")
        val none = Trust("none")

        @JvmField
        val CREATOR = object : Parcelable.Creator<Trust> {
            override fun createFromParcel(parcel: Parcel) = Trust(parcel)

            override fun newArray(size: Int): Array<Trust?>  = arrayOfNulls(size)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
    }

    override fun describeContents(): Int {
        return 0
    }
}
