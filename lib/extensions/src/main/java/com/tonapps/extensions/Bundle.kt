package com.tonapps.extensions

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import java.io.Serializable

fun Bundle.getStringValue(vararg keys: String): String? {
    for (key in keys) {
        val value = getString(key)
        if (value.isNullOrBlank()) {
            continue
        }
        return value
    }
    return null
}

inline fun <reified T : Serializable> Bundle.getSerializableCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(key, T::class.java)
    } else {
        getSerializable(key) as T?
    }
}

inline fun <reified T : Enum<T>> Bundle.getEnum(key: String, def: T): T {
    return (getSerializableCompat(key) as T?) ?: def
}

fun <T : Enum<T>> Bundle.putEnum(key: String, value: T) {
    putSerializable(key, value)
}

inline fun <reified R> Bundle.getParcelableCompat(key: String): R? {
    return try {
        BundleCompat.getParcelable(this, key, R::class.java)
    } catch (e: Exception) {
        null
    }
}

fun Bundle.print(): String {
    val sb = StringBuilder()
    sb.append("Bundle {")
    keySet().forEach { key ->
        sb.append("\n  $key: ${get(key)}")
    }
    sb.append("\n}")
    return sb.toString()
}