package com.tonapps.signer.extensions

import android.content.SharedPreferences
import org.ton.crypto.hex

fun SharedPreferences.getByteArray(key: String): ByteArray? {
    val value = getString(key, null)
    if (value.isNullOrBlank()) {
        return null
    }
    return hex(value)
}

fun SharedPreferences.Editor.putByteArray(key: String, value: ByteArray): SharedPreferences.Editor {
    putString(key, hex(value))
    return this
}