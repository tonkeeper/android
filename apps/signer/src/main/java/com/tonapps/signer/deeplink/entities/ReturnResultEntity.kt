package com.tonapps.signer.deeplink.entities

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.tonapps.signer.extensions.uriOrNull

data class ReturnResultEntity(
    val toApp: Boolean,
    val uri: Uri?
): Parcelable {

    val name: String
        get() = uri?.host ?: "App"

    constructor(parcel: Parcel) : this(
        parcel.readByte() != 0.toByte(),
        parcel.readString()?.uriOrNull
    )

    constructor(toApp: Boolean, url: String?) : this(
        toApp = toApp,
        uri = url?.uriOrNull
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (toApp) 1 else 0)
        parcel.writeString(uri?.toString())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ReturnResultEntity> {
        override fun createFromParcel(parcel: Parcel): ReturnResultEntity {
            return ReturnResultEntity(parcel)
        }

        override fun newArray(size: Int): Array<ReturnResultEntity?> {
            return arrayOfNulls(size)
        }
    }
}