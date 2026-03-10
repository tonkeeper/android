package com.tonapps.blockchain.ton.connect

import com.tonapps.extensions.toByteArray
import org.ton.block.AddrStd
import java.nio.ByteOrder

data class TCAddress(val value: AddrStd): TCSerializable {

    override fun toByteArray(order: ByteOrder): ByteArray {
        val workchainBuffer = value.workchainId.toByteArray(order)
        val addressBuffer = value.address.toByteArray()
        return workchainBuffer + addressBuffer
    }
}