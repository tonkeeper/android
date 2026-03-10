package com.tonapps.tonkeeper.extensions

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.consistentBucketFor(size: Int): Int {
    val buffer = ByteBuffer.wrap(copyOf(4)).order(ByteOrder.BIG_ENDIAN)
    val u32 = Integer.toUnsignedLong(buffer.int)
    val index = (u32 % size).toInt()
    buffer.clear()
    return index
}

fun ByteArray?.isNullOrEmpty(): Boolean {
    return this == null || this.isEmpty()
}