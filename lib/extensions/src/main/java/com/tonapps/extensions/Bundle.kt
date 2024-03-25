package com.tonapps.extensions

import android.os.Build
import android.os.Bundle
import androidx.core.os.BundleCompat
import java.io.Serializable

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
