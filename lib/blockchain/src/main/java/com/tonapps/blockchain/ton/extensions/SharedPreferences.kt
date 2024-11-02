package com.tonapps.blockchain.ton.extensions

import android.content.SharedPreferences
import android.util.Base64
import com.tonapps.base64.decodeBase64
import org.ton.api.pk.PrivateKeyEd25519

fun SharedPreferences.getPrivateKey(key: String): PrivateKeyEd25519? {
    return getPrivateKey2(key) ?: getPrivateKey1(key)
}

private fun SharedPreferences.getPrivateKey1(key: String): PrivateKeyEd25519? {
    try {
        val base64 = getString(key, null)
        if (base64.isNullOrEmpty()) {
            return null
        }
        return PrivateKeyEd25519(base64.decodeBase64())
    } catch (e: Throwable) {
        return null
    }
}

private fun SharedPreferences.getPrivateKey2(key: String): PrivateKeyEd25519? {
    try {
        val base64 = getString(key, null)
        if (base64.isNullOrEmpty()) {
            return null
        }
        return PrivateKeyEd25519(Base64.decode(base64, Base64.DEFAULT))
    } catch (e: Throwable) {
        return null
    }
}