package com.tonapps.tonkeeper.os

import android.os.Build

fun isMediatek() = Build.HARDWARE?.startsWith("mt", ignoreCase = true) ?: false