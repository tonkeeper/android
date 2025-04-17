package com.tonapps.tonkeeper.os

import android.content.Context
import android.os.Build

object AppInstall {

    enum class Source(val packageName: String, val title: String) {
        GOOGLE_PLAY("com.android.vending", "GooglePlay"),
        UNKNOWN("unknown", "APK")
    }

    fun request(context: Context): Source {
        val installerPackageName = getInstallerPackageName(context)
        return when (installerPackageName) {
            Source.GOOGLE_PLAY.packageName -> Source.GOOGLE_PLAY
            else -> Source.UNKNOWN
        }
    }

    private fun getInstallerPackageName(context: Context): String? {
        try {
            val packageManager = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            return null
        }
    }
}