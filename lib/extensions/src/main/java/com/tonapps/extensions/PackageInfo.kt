package com.tonapps.extensions

import android.content.pm.PackageInfo
import android.os.Build

val PackageInfo.versionCodeCompat: Long
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
    }