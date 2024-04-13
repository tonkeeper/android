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