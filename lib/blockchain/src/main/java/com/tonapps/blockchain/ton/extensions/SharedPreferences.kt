package com.tonapps.blockchain.ton.extensions

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.base64.decodeBase64
import io.ktor.util.decodeBase64Bytes
import org.ton.api.pk.PrivateKeyEd25519

fun SharedPreferences.getPrivateKey(key: String): PrivateKeyEd25519? {
    val base64 = getString(key, null)
    if (base64.isNullOrEmpty()) {
        return null
    }
    return decodePrivateKey2(key) ?: decodePrivateKey1(key) ?: decodePrivateKey3(key)
}

// Bad hack to decode private key....

private fun decodePrivateKey1(base64: String): PrivateKeyEd25519? {
    try {
        return PrivateKeyEd25519(base64.decodeBase64())
    } catch (e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
        return null
    }
}

private fun decodePrivateKey2(base64: String): PrivateKeyEd25519? {
    try {
        return PrivateKeyEd25519(Base64.decode(base64, Base64.DEFAULT))
    } catch (e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
        return null
    }
}

private fun decodePrivateKey3(base64: String): PrivateKeyEd25519? {
    try {
        return PrivateKeyEd25519(base64.decodeBase64Bytes())
    } catch (e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
        return null
    }
}