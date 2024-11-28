package com.tonapps.wallet.api.entity

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppVersion(
    val value: String
): Parcelable {

    @IgnoredOnParcel
    val integer: Int by lazy {
        value.replace(".", "").toIntOrNull() ?: 0
    }
}