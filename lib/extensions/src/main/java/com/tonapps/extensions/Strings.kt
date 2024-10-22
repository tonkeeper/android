package com.tonapps.extensions

import android.graphics.Color
import android.net.Uri
import androidx.core.net.toUri

val String.short12: String
    get() {
        if (length < 24) return this
        return substring(0, 12) + "…" + substring(length - 12, length)
    }

val String.short8: String
    get() {
        if (length < 16) return this
        return substring(0, 8) + "…" + substring(length - 8, length)
    }

val String.short6: String
    get() {
        if (length < 12) return this
        return substring(0, 6) + "…" + substring(length - 6, length)
    }

val String.short4: String
    get() {
        if (length < 8) return this
        return substring(0, 4) + "…" + substring(length - 4, length)
    }

val String.max12: String
    get() {
        if (length < 12) return this
        return substring(0, 12) + "…"
    }

val String.max18: String
    get() {
        if (length < 18) return this
        return substring(0, 18) + "…"
    }

val String.max24: String
    get() {
        if (length < 24) return this
        return substring(0, 24) + "…"
    }

val String.color: Int
    get() {
        return try {
            if (startsWith("#")) {
                Color.parseColor(this)
            } else {
                Color.parseColor("#$this")
            }
        } catch (e: Exception) {
            0
        }
    }


fun String.fromHex(): ByteArray {
    val len = length
    if (len % 2 != 0) {
        throw IllegalArgumentException("Invalid hex string")
    }
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

fun String.toUriOrNull(): Uri? {
    return try {
        val uri = toUri()
        if (uri.scheme.isNullOrBlank()) {
            null
        } else {
            uri
        }
    } catch (e: Throwable) {
        null
    }
}


