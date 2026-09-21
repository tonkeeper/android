package com.tonapps.wallet.data.battery.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BatteryPackageEntity(
    val name: String,
    val image: String,
    val charges: Int,
) : Parcelable
