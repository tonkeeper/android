package com.tonapps.extensions

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import androidx.annotation.RawRes
import java.io.File
import java.util.Locale

val Context.locale: Locale
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales.get(0)
        } else {
            resources.configuration.locale
        }
    }

val Context.isDebug: Boolean
    get() = 0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE

fun Context.cacheFolder(name: String): File {
    return cacheDir.folder(name)
}

fun Context.raw(@RawRes id: Int): ByteArray {
    return resources.openRawResource(id).readBytes()
}

fun Context.rawText(@RawRes id: Int): String {
    return raw(id).toString(Charsets.UTF_8)
}

val Context.packageInfo: PackageInfo
    get() = packageManager.getPackageInfo(packageName, 0)

fun Context.prefs(name: String): SharedPreferences {
    return getSharedPreferences(name, Context.MODE_PRIVATE)
}