package com.tonapps.extensions

import android.os.Build

object Os {
    fun deviceNameAndModel(): String {
        return if (Build.MODEL.startsWith(Build.MANUFACTURER, true)) {
            Build.MODEL.titlecase()
        } else {
            Build.MANUFACTURER.titlecase() + " " + Build.MODEL
        }
    }
}
