package com.tonapps.blockchain.ton.connect

import java.nio.ByteOrder

interface TCSerializable {
    fun toByteArray(order: ByteOrder): ByteArray
}