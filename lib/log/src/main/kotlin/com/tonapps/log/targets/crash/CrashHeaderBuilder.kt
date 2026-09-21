package com.tonapps.log.targets.crash

import android.content.Context
import android.os.Build
import com.tonapps.log.utils.LogHeaderBuilder

internal class CrashHeaderBuilder(
    context: Context,
) : LogHeaderBuilder() {

    private val appSettingsProvider = LogHeaderBuilder.AppSettingsProvider(context)

    override fun build(): StringBuilder {
        fillAppInfo()
        fillDeviceInfo()

        return super.build()
    }

    private fun fillAppInfo() {
        appSettingsProvider.provide()
            .forEach { (k, v) ->
                add(k, v)
            }
    }

    private fun fillDeviceInfo() {
        add("VERSION_CODENAME", Build.VERSION.CODENAME)
        add("SDK CODE", Build.VERSION.SDK_INT.toString())
        add("MANUFACTURER", Build.MANUFACTURER)
        add("MODEL", Build.MODEL)
        add("BOARD", Build.BOARD)
        add("BRAND", Build.BRAND)
        add("DEVICE", Build.DEVICE)
        add("HARDWARE", Build.HARDWARE)
        add("DISPLAY", Build.DISPLAY)
        add("FINGERPRINT", Build.FINGERPRINT)
        add("PRODUCT", Build.PRODUCT)
        add("USER", Build.USER)
    }
}
