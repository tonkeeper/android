package com.tonapps.bus.core.aptabase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.tonapps.extensions.packageInfo
import com.tonapps.extensions.versionCodeCompat
import java.util.Locale

private const val SDK_VERSION = "aptabase-kotlin@0.0.8"
private const val OS_NAME = "Android"

data class AptabaseEnvironment(
    val isDebug: Boolean,
    val osVersion: String,
    val locale: String,
    val appVersion: String,
    val appBuildNumber: String,
    val deviceModel: String,
) {

    val systemProps = AptabaseEvent.SystemProps(
            isDebug = isDebug,
            osName = OS_NAME,
            osVersion = osVersion,
            locale = locale,
            appVersion = appVersion,
            appBuildNumber = appBuildNumber,
            sdkVersion = SDK_VERSION,
            deviceModel = deviceModel,
        )

    companion object {

        fun current(context: Context): AptabaseEnvironment {
            val packageInfo = context.packageInfo
            return AptabaseEnvironment(
                isDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                osVersion = Build.VERSION.RELEASE ?: "",
                locale = Locale.getDefault().language,
                appVersion = packageInfo.versionName ?: "",
                appBuildNumber = packageInfo.versionCodeCompat.toString(),
                deviceModel = Build.MODEL,
            )
        }
    }
}
