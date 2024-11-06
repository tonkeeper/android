package com.tonapps.blockchain.ton.extensions

import android.util.Base64
import com.tonapps.base64.decodeBase64
import com.tonapps.base64.encodeBase64
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex

fun String.publicKeyFromBase64(): PublicKeyEd25519 {
    Base64.decode(this, Base64.DEFAULT).let {
        return PublicKeyEd25519(it)
    }
}

fun String.publicKeyFromHex(): PublicKeyEd25519 {
    return PublicKeyEd25519(hex(this))
}

fun PublicKeyEd25519.base64(): String {
    return Base64.encodeToString(key.toByteArray(), Base64.DEFAULT)
}

fun PublicKeyEd25519.hex(): String {
    return hex(key.toByteArray())
}