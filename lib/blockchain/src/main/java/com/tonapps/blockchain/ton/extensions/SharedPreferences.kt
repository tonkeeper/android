package com.tonapps.blockchain.ton.extensions

import android.content.SharedPreferences
import android.util.Base64
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.base64.fixBase64
import org.ton.api.pk.PrivateKeyEd25519

fun SharedPreferences.getPrivateKey(key: String): PrivateKeyEd25519? {
    val base64 = getString(key, null)
    if (base64.isNullOrEmpty()) {
        return null
    }
    return decodePrivateKey(base64)
}

fun decodePrivateKey(base64: String): PrivateKeyEd25519? {
    return decodePrivateKey1(base64) ?: decodePrivateKey2(base64)
}

private fun decodePrivateKey1(base64: String): PrivateKeyEd25519? {
    return try {
        PrivateKeyEd25519(Base64.decode(base64, Base64.DEFAULT))
    } catch (e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
        null
    }
}

// Sometime we need to decode base64 string with fix padding
private fun decodePrivateKey2(base64: String): PrivateKeyEd25519? {
    return decodePrivateKey1(base64.fixBase64())
}
