package com.tonapps.ledger.transport

import java.nio.ByteBuffer

interface Transport {
    suspend fun exchange(
        apdu: ByteArray,
    ): ByteArray

    suspend fun send(
        system: Int,
        command: Int,
        p1: Int,
        p2: Int,
        data: ByteArray = ByteArray(0),
        responseCodes: List<Int>?
    ): ByteArray {
        val buffer = ByteBuffer.allocate(5 + data.size)
        buffer.put(system.toByte())
        buffer.put(command.toByte())
        buffer.put(p1.toByte())
        buffer.put(p2.toByte())
        buffer.put(data.size.toByte())
        buffer.put(data)
        return exchange(buffer.array())
    }
}
