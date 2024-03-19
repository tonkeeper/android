package com.tonapps.extensions

import android.content.Context
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

fun Context.cacheFolder(name: String): File {
    return cacheDir.folder(name)
}

fun Context.raw(@RawRes id: Int): ByteArray {
    return resources.openRawResource(id).readBytes()
}

fun Context.rawText(@RawRes id: Int): String {
    return raw(id).toString(Charsets.UTF_8)
}