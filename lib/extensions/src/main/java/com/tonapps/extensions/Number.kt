package com.tonapps.extensions

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Long.toByteArray(order: ByteOrder): ByteArray {
    return ByteBuffer.allocate(8).order(order).putLong(this).array()
}

fun Int.toByteArray(order: ByteOrder): ByteArray {
    return ByteBuffer.allocate(4).order(order).putInt(this).array()
}
