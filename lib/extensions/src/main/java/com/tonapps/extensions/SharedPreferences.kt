package com.tonapps.extensions

import android.content.SharedPreferences
import androidx.core.content.edit

fun SharedPreferences.getByteArray(key: String): ByteArray? {
    val bytes = getString(key, null)?.base64 ?: return null
    if (bytes.isEmpty()) {
        return null
    }
    return bytes
}

inline fun <reified T : Enum<T>> SharedPreferences.getEnum(key: String, default: T): T {
    val value = this.getInt(key, -1)
    return if (value >= 0) {
        enumValues<T>()[value]
    } else {
        default
    }
}

fun <T : Enum<T>> SharedPreferences.Editor.putEnum(key: String, value: T?) = apply {
    this.putInt(key, value?.ordinal ?: -1)
}

fun SharedPreferences.string(key: String): String? {
    return this.getString(key, null)
}

fun SharedPreferences.string(key: String, value: String?) {
    edit {
        if (value.isNullOrBlank()) {
            remove(key)
        } else {
            putString(key, value)
        }
    }
}