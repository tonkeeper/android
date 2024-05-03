package com.tonapps.tonkeeper

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.wallet.api.entity.FlagsEntity

val Context.featureFlags: FlagsEntity
    get() = this.remoteConfig?.flags ?: FlagsEntity()

val Context.isLowDevice: Boolean
    get() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.isLowRamDevice
    }

val Context.isBlurDisabled: Boolean
    get() = isLowDevice || featureFlags.disableBlur || (Build.VERSION_CODES.S > Build.VERSION.SDK_INT && featureFlags.disableLegacyBlur)