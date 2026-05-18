package com.tonapps.blockchain.utils

import java.math.BigInteger

const val HexPrefix = "0x"

fun String.drop0x(): String = if (containsHexPrefix()) {
    substring(2)
} else {
    this
}

fun String.add0x(): String =
    if (startsWith(HexPrefix)) {
        this
    } else {
        "$HexPrefix$this"
    }

fun String.remove0x(): String =
    removePrefix(HexPrefix)

fun String.hexToBigInteger(default: BigInteger = BigInteger.ZERO): BigInteger {
    return try {
        remove0x().toBigInteger(16)
    } catch (e: NumberFormatException) {
        default
    }
}

fun String.containsHexPrefix(): Boolean =
    this.length > 1 && this[0] == '0' && this[1] == 'x'

fun String.isHexEncoded(): Boolean {
    val regex = "^0x[0-9A-Fa-f]*$".toRegex()

    if (!this.containsHexPrefix()) {
        return false
    }

    if (!regex.matches(this)) {
        return false
    }

    return true
}
