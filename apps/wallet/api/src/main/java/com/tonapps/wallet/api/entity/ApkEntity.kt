package com.tonapps.wallet.api.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkEntity(
    val apkDownloadUrl: String = "",
    val apkName: AppVersion = AppVersion("5.0.21"),
): Parcelable