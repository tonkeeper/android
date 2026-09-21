package com.tonapps.blockchain.utils

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger

const val HexPrefix = "0x"

fun String.add0x(): String =
    if (startsWith(HexPrefix)) {
        this
    } else {
        "$HexPrefix$this"
    }

fun String.remove0x(): String =
    removePrefix(HexPrefix)

fun String?.hexToBigInteger(default: BigInteger = BigInteger.ZERO): BigInteger {
    return try {
        this
            ?.remove0x()
            ?.toBigInteger(16)
            ?: default
    } catch (e: NumberFormatException) {
        default
    }
}

fun String.containsHexPrefix(): Boolean =
    this.length > 1 && this[0] == '0' && this[1] == 'x'

/** Loose shape check for name-service domains (`foo.ton`, `foo.t.me`, `t.me/foo`, ENS, etc.) — resolution decides actual validity. */
fun String.isWeb3DomainName(): Boolean {
    val value = trim()
    val dot = value.indexOf('.')
    return dot > 0 && dot < value.lastIndex && value.none { it.isWhitespace() }
}
