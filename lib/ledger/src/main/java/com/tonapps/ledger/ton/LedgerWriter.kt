package com.tonapps.ledger.ton

import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.math.ceil

object LedgerWriter {
    fun putUint32(value: Int): ByteArray {
        return ByteBuffer.allocate(4).apply {
            putInt(value)
        }.array()
    }

    fun putUint16(value: Int): ByteArray {
        return ByteBuffer.allocate(2).apply {
            putShort(value.toShort())
        }.array()
    }

    fun putUint64(value: BigInteger): ByteArray {
        return CellBuilder.createCell { storeUInt(value, 64) }.beginParse().bits.toByteArray()
    }

    fun putVarUInt(value: Long): ByteArray {
        val sizeBytes = if (value == 0L) 0 else ceil(value.toString(2).length / 8.0).toInt()
        return CellBuilder.createCell {
            storeUInt(sizeBytes, 8)
            storeUInt(value, sizeBytes * 8)
        }.beginParse().loadBits(8 + sizeBytes * 8).toByteArray()
    }

    fun putUint8(value: Int): ByteArray {
        return byteArrayOf(value.toByte())
    }

    fun putAddress(address: AddrStd): ByteArray {
        return putUint8(if (address.workchainId == -1) 0xff else address.workchainId) + address.address.toByteArray()
    }

    fun putCellRef(ref: Cell): ByteArray {
        return putUint16(ref.depth()) + ref.hash()
    }
}