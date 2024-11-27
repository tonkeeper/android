package com.tonapps.extensions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.net.URL
import java.net.URLConnection

val Uri.isLocal: Boolean
    get() = scheme == "file" || scheme == "content" || scheme == "res"

fun Uri.getQueryParameterSafe(key: String): String? {
    return try {
        getQueryParameter(key)
    } catch (e: Exception) {
        null
    }
}

fun Uri.getQueryLong(key: String): Long? {
    return getQueryParameterSafe(key)?.toLongOrNull()
}

fun Uri.hasQuery(key: String): Boolean {
    return getQueryParameterSafe(key) != null
}

val Uri.isEmptyQuery: Boolean
    get() = queryParameterNames.isEmpty()

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
        val value = getQueryParameterSafe(key)
        if (value != null) {
            return value
        }
    }
    return null
}

val Uri.withoutQuery: Uri
    get() = buildUpon().clearQuery().build()

val Uri.pathOrNull: String?
    get() = path?.replace("/", "")?.ifBlank { null }

val Uri.hostOrNull: String?
    get() = host?.ifBlank { null }

fun Uri.containsQuery(key: String): Boolean {
    return query(key) != null
}

fun Uri.query(key: String): String? {
    return getQueryParameterSafe(key)?.trim()?.ifBlank { null }
}

fun Uri.queryLong(key: String): Long? {
    return query(key)?.toLongOrNull()
}

fun Uri.queryPositiveLong(key: String): Long? {
    return queryLong(key)?.takeIf { it > 0 }
}

fun Uri.queryBoolean(key: String, defValue: Boolean = false): Boolean {
    val value = query(key) ?: return defValue
    return value.equals("true", ignoreCase = true) || value == "1"
}

fun Uri.hasUnsupportedQuery(
    includingUTM: Boolean = false,
    vararg supportedQuery: String
): Boolean {
    return queryParameterNames.any {
        if (includingUTM) {
            it.startsWith("utm_") || it !in supportedQuery
        } else {
            it !in supportedQuery
        }
    }
}

