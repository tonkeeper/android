package com.tonapps.blockchain.tron

import com.google.protobuf.ByteString
import org.bitcoinj.core.Base58

fun String.encodeTronAddress(): String {
    val decoded: ByteArray = Base58.decodeChecked(this)
    return decoded.copyOfRange(1, 21).joinToString("") { "%02x".format(it) }.padStart(64, '0')
}

fun String.tronHex(): String {
    return Base58.decodeChecked(this).joinToString("") { "%02x".format(it) }
}

fun String.toEvmHex(): String {
    val fullHex = Base58.decodeChecked(this).joinToString("") { "%02x".format(it) }
    return fullHex.removePrefix("41")
}

fun String.isValidTronAddress(): Boolean {
    return this.length == 34 && this.startsWith("T") && try {
        Base58.decodeChecked(this)
        true
    } catch (e: Exception) {
        false
    }
}
