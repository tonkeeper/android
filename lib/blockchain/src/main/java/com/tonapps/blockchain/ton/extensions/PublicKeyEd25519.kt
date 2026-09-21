package com.tonapps.blockchain.ton.extensions

import android.util.Base64
import io.ktor.util.hex
import kotlinx.io.bytestring.ByteString
import org.ton.kotlin.crypto.PublicKeyEd25519

fun String.publicKeyFromBase64(): PublicKeyEd25519 {
    Base64.decode(this, Base64.DEFAULT).let {
        return PublicKeyEd25519(ByteString(it))
    }
}

fun String.publicKeyFromHex(): PublicKeyEd25519 {
    return PublicKeyEd25519(ByteString(hex(this)))
}

fun PublicKeyEd25519.base64(): String {
    return Base64.encodeToString(key.toByteArray(), Base64.DEFAULT)
}

fun PublicKeyEd25519.hex(): String {
    return hex(key.toByteArray())
}