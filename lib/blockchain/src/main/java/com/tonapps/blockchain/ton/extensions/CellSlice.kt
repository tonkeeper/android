package com.tonapps.blockchain.ton.extensions

import com.tonapps.blockchain.ton.TONOpCode
import org.ton.bigint.BigInt
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.tlb.loadTlb

fun CellSlice.loadOpCode(): TONOpCode {
    val code = loadUInt32()
    val long = code.toLong()
    return TONOpCode.entries.firstOrNull { it.code == long } ?: TONOpCode.UNKNOWN
}

fun CellSlice.loadMaybeRef(): Cell? {
    if (!loadBit()) {
        return null
    }
    return loadRef()
}

fun CellSlice.loadMaybeAddress(): MsgAddress? {
    return when (val type = preloadUInt(2)) {
        BigInt.valueOf(2) -> loadAddress()
        BigInt.valueOf(0) -> {
            bitsPosition += 2
            null
        }
        else -> throw RuntimeException("Invalid address type: $type")
    }
}

fun CellSlice.loadAddress(): MsgAddressInt {
    // loadTlb(MsgAddressInt.tlbCodec())
    return loadTlb(MsgAddressInt)

}

fun CellSlice.loadCoins(): Coins {
    return loadTlb(Coins)
}