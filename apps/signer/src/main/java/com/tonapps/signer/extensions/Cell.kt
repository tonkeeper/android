package com.tonapps.signer.extensions

import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.crypto.base64

fun String.parseCell(): Cell {
    return BagOfCells(base64(this)).first()
}

fun String.safeParseCell(): Cell? {
    return try {
        parseCell()
    } catch (e: Throwable) {
        Cell.empty()
    }
}

val String.isValidCell: Boolean
    get() = safeParseCell() != null

fun String.parseCellOrEmpty(): Cell {
    return safeParseCell() ?: Cell.empty()
}

fun Cell.base64(): String {
    return base64(BagOfCells(this).toByteArray())
}

fun CellSlice.loadRemainingBits(): BitString {
    return BitString((this.bitsPosition until this.bits.size).map { this.loadBit() })
}
