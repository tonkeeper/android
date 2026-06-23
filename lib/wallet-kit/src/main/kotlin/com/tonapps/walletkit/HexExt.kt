package com.tonapps.walletkit

private const val HEX_PREFIX = "0x"

/**
 * Ensures the string starts with "0x".
 */
fun String.add0x(): String =
    if (startsWith(HEX_PREFIX)) this else "$HEX_PREFIX$this"
