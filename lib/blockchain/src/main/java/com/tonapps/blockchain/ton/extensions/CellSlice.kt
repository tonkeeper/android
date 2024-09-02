package com.tonapps.blockchain.ton.extensions

import com.tonapps.blockchain.ton.TONOpCode
import org.ton.cell.CellSlice

fun CellSlice.loadOpCode(): TONOpCode {
    val code = loadUInt32()
    val long = code.toLong()
    return TONOpCode.entries.firstOrNull { it.code == long } ?: TONOpCode.UNKNOWN
}