package com.tonapps.signer.extensions

import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.base64

fun String.publicKey(): PublicKeyEd25519 {
    return PublicKeyEd25519(base64())
}

fun String.safePublicKey(): PublicKeyEd25519? {
    return try {
        PublicKeyEd25519(base64())
    } catch (e: Throwable) {
        null
    }
}

fun PublicKeyEd25519.base64(): String {
    return base64(key.toByteArray())
}