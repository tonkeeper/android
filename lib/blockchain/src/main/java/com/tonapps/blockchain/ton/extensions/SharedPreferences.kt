package com.tonapps.blockchain.ton.extensions

import android.util.Base64
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.base64.fixBase64
import org.ton.api.pk.PrivateKeyEd25519

fun String.decodePrivateKey(): PrivateKeyEd25519? {
    if (isNullOrEmpty()) {
        return null
    }

    return runCatching { decodePrivateKey1(this) }
        .getOrNull()
        ?: decodePrivateKey2(this)
}

private fun decodePrivateKey1(base64: String): PrivateKeyEd25519? {
    return try {
        PrivateKeyEd25519(Base64.decode(base64, Base64.DEFAULT))
    } catch (e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
        throw e
    }
}

// Sometime we need to decode base64 string with fix padding
private fun decodePrivateKey2(base64: String): PrivateKeyEd25519? {
    return decodePrivateKey1(base64.fixBase64())
}
