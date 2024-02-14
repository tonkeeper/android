package com.tonapps.extensions

import android.content.SharedPreferences

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