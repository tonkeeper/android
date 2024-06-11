package com.tonapps.extensions

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import androidx.annotation.RawRes
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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

val Context.appVersionName: String
    get() = packageInfo.versionName

val Context.appVersionCode: Long
    get() = packageInfo.versionCodeCompat

val Context.isMainVersion: Boolean
    get() = packageInfo.packageName == "com.ton_keeper"

fun Context.prefs(name: String): SharedPreferences {
    return getSharedPreferences(name, Context.MODE_PRIVATE)
}

fun Context.prefsEncrypted(name: String): SharedPreferences {
    if (isMainVersion) {
        return prefs(name)
    }
    return EncryptedSharedPreferences.create(
        name,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}