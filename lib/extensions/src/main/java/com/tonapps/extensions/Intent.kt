package com.tonapps.extensions

import android.content.Context
import android.content.Intent

fun Intent.isValid(context: Context): Boolean {
    val packageManager = context.packageManager
    val resolveInfo = packageManager.resolveActivity(this, 0)
    return resolveInfo?.activityInfo != null
}