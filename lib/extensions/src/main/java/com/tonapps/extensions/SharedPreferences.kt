package com.tonapps.extensions

import android.content.SharedPreferences
import android.os.Parcelable
import android.util.Base64
import androidx.core.content.edit


fun SharedPreferences.Editor.putParcelable(key: String, value: Parcelable?) {
    if (value == null) {
        remove(key)
    } else {
        putByteArray(key, value.toByteArray())
    }
}

fun SharedPreferences.getByteArray(key: String): ByteArray? {
    val value = run {
        val value = getString(key, null)
        if (value.isNullOrBlank()) {
            return null
        }

        Base64.decode(value, Base64.DEFAULT)
    }
    return value
}

fun SharedPreferences.Editor.putByteArray(key: String, value: ByteArray?) = apply {
    val string = Base64.encodeToString(value, Base64.DEFAULT)
    if (string == null) {
        remove(key)
    } else {
        putString(key, string)
    }
}

inline fun <reified R: Parcelable> SharedPreferences.getParcelable(key: String): R? {
    val bytes = getByteArray(key) ?: return null
    return bytes.toParcel<R>()
}

fun SharedPreferences.getIntArray(key: String): IntArray? {
    if (!contains(key)) {
        return null
    }
    val value = getString(key, null) ?: return null
    return value.split(",").mapNotNull { it.toIntOrNull() }.toIntArray()
}

fun SharedPreferences.putParcelable(key: String, value: Parcelable?) {
    if (value == null) {
        remove(key)
    } else {
        edit {
            putByteArray(key, value.toByteArray())
        }
    }
}

fun SharedPreferences.putIntArray(key: String, value: IntArray) {
    edit {
        putString(key, value.joinToString(","))
    }
}

fun SharedPreferences.remove(key: String) {
    edit {
        remove(key)
    }
}

fun SharedPreferences.clear() {
    edit {
        clear()
    }
}

fun SharedPreferences.putString(key: String, value: String?) {
    if (value.isNullOrBlank()) {
        remove(key)
    } else {
        edit {
            putString(key, value)
        }
    }
}

fun SharedPreferences.putLong(key: String, value: Long) {
    edit {
        putLong(key, value)
    }
}

fun SharedPreferences.putInt(key: String, value: Int) {
    edit {
        putInt(key, value)
    }
}

fun SharedPreferences.putBoolean(key: String, value: Boolean) {
    edit {
        putBoolean(key, value)
    }
}

fun SharedPreferences.string(key: String): String? {
    val value = this.getString(key, null)
    if (value.isNullOrEmpty()) {
        return null
    }
    return value
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