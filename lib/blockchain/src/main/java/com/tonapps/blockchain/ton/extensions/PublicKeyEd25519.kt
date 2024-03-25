package com.tonapps.blockchain.ton.extensions

import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.base64
import org.ton.crypto.hex

fun String.publicKey(): PublicKeyEd25519 {
    return try {
        PublicKeyEd25519(base64())
    } catch (e: Throwable) {
        PublicKeyEd25519(hex(this))
    }
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