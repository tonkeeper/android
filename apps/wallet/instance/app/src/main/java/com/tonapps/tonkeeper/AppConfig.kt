package com.tonapps.tonkeeper

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager

val Context.batteryLevel: Int
    get() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

val Context.isLowDevice: Boolean
    get() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.isLowRamDevice
    }

val Context.isBlurDisabled: Boolean
    get() = isLowDevice && 20 >= batteryLevel