package com.tonapps.extensions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.net.URL
import java.net.URLConnection

val Uri.isLocal: Boolean
    get() = scheme == "file" || scheme == "content" || scheme == "res"


fun Uri.getQueryLong(key: String): Long? {
    return getQueryParameter(key)?.toLongOrNull()
}

fun Uri.getBitmap(): Bitmap? {
    return try {
        val connection: URLConnection = URL(toString()).openConnection()
        connection.connect()
        connection.getInputStream().use {
            Bitmap.createBitmap(BitmapFactory.decodeStream(it))
        }
    } catch (e: Exception) {
        null
    }
}

fun Uri.getMultipleQuery(vararg keys: String): String? {
    for (key in keys) {
        val value = getQueryParameter(key)
        if (value != null) {
            return value
        }
    }
    return null
}

val Uri.withoutQuery: Uri
    get() = buildUpon().clearQuery().build()