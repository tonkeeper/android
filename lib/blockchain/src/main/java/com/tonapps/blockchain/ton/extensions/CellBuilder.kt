package com.tonapps.blockchain.ton.extensions

import com.tonapps.blockchain.ton.TONOpCode
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellBuilder.Companion.beginCell
import org.ton.tlb.CellRef
import org.ton.tlb.TlbCodec
import org.ton.tlb.storeRef
import org.ton.tlb.storeTlb
import java.math.BigInteger
import kotlin.math.floor

val CellBuilder.availableBits: Int
    get() = 1023 - bits.size

fun CellBuilder.storeBuilder(builder: CellBuilder) = apply {
    storeRefs(builder.refs)
    storeBits(builder.bits)
}

fun CellBuilder.storeOpCode(opCode: TONOpCode) = apply {
    storeUInt(opCode.code, 32)
}

fun CellBuilder.storeSeqAndValidUntil(seqNo: Int, validUntil: Long) = apply {
    if (seqNo == 0) {
        for (i in 0 until 32) {
            storeBit(true)
        }
    } else {
        storeUInt(validUntil, 32)
    }
    storeUInt(seqNo, 32)
}

fun CellBuilder.storeStringTail(src: String) = apply {
    writeBytes(src.toByteArray(), this)
}

fun CellBuilder.storeMaybeStringTail(src: String?) = apply {
    if (src.isNullOrEmpty()) {
        storeBit(false)
    } else {
        storeBit(true)
        storeStringTail(src)
    }
}

fun CellBuilder.storeStringRefTail(src: String) = apply {
    storeRef(beginCell().storeStringTail(src).endCell())
}

private fun writeBytes(src: ByteArray, builder: CellBuilder) {
    if (src.isNotEmpty()) {
        val bytes = floor(builder.availableBits / 8f).toInt()
        if (src.size > bytes) {
            val a = src.copyOfRange(0, bytes)
            val t = src.copyOfRange(bytes, src.size)
            builder.storeBytes(a)
            val bb = beginCell()
            writeBytes(t, bb)
            builder.storeRef(bb.endCell())
        } else {
            builder.storeBytes(src)
        }
    }
}

fun <T> CellBuilder.storeMaybeRef(codec: TlbCodec<T>, value: CellRef<T>?) = apply {
    if (value == null) {
        storeBit(false)
    } else {
        storeBit(true)
        storeRef(codec, value)
    }
}

fun CellBuilder.storeMaybeRef(value: Cell?) = apply {
    if (value == null) {
        storeBit(false)
    } else {
        storeBit(true)
        storeRef(value)
    }
}

fun CellBuilder.storeMaybeAddress(value: MsgAddressInt?) = apply {
    if (value == null) {
        storeBit(false)
    } else {
        storeBit(true)
        storeAddress(value)
    }
}

fun CellBuilder.storeCoins(value: Coins) = apply {
    storeTlb(Coins, value)
}

fun CellBuilder.storeCoins(value: Long) = apply {
    storeCoins(Coins.ofNano(value))
}

fun CellBuilder.storeAddress(value: MsgAddressInt) = apply {
    storeTlb(MsgAddressInt, value)
}

fun CellBuilder.storeAddress(value: MsgAddress) = apply {
    if (value is MsgAddressInt) {
        storeAddress(value)
    } else {
        throw IllegalArgumentException("Unsupported address type")
    }
}

fun CellBuilder.storeQueryId(value: BigInteger) = apply {
    storeUInt(value, 64)
}