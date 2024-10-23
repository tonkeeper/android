package com.tonapps.blockchain.ton.extensions

import com.tonapps.base64.decodeBase64
import com.tonapps.base64.encodeBase64
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex

fun String.publicKey(): PublicKeyEd25519 {
    return try {
        PublicKeyEd25519(hex(this))
    } catch (e: Throwable) {
        PublicKeyEd25519(decodeBase64())
    }
}

fun String.safePublicKey(): PublicKeyEd25519? {
    return try {
        publicKey()
    } catch (e: Throwable) {
        null
    }
}

fun PublicKeyEd25519.base64(): String {
    return key.toByteArray().encodeBase64()
}

fun PublicKeyEd25519.hex(): String {
    return hex(key.toByteArray())
}