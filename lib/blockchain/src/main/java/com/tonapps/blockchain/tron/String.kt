package com.tonapps.blockchain.tron

import com.tonapps.chainkit.chain.tron.utils.TronUtils

fun String.encodeTronAddress(): String {
    return toEvmHex().padStart(64, '0')
}

// The 21 address bytes as hex: "41" and the 20 bytes of the address.
fun String.tronHex(): String {
    return TronUtils.addressHex(this) ?: throw IllegalArgumentException("Invalid TRON address")
}

fun String.toEvmHex(): String {
    return tronHex().removePrefix("41")
}

fun String.isValidTronAddress(): Boolean {
    return TronUtils.isValidAddress(this)
}
