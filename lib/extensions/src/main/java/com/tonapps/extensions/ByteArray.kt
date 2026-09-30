package com.tonapps.extensions

fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val b = byte.toInt() and 0xFF
        append(Character.forDigit(b shr 4, 16))
        append(Character.forDigit(b and 0x0F, 16))
    }
}
