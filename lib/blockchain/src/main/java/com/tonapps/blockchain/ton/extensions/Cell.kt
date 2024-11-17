package com.tonapps.blockchain.ton.extensions

import com.tonapps.base64.decodeBase64
import com.tonapps.base64.encodeBase64
import org.json.JSONObject
import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.crypto.hex

fun String.bocFromBase64(): BagOfCells {
    if (startsWith("{")) {
        throw IllegalArgumentException("js objects are not supported")
    }
    return BagOfCells(decodeBase64())
}

fun String.bocFromHex(): BagOfCells {
    if (startsWith("{")) {
        throw IllegalArgumentException("js objects are not supported")
    }
    return BagOfCells(hex(this))
}

fun String.cellFromBase64(): Cell {
    if (this.isBlank()) {
        throw IllegalArgumentException("Empty cell")
    }
    val parsed = bocFromBase64()
    if (parsed.roots.size != 1) {
        throw IllegalArgumentException("Deserialized more than one cell")
    }
    return parsed.first()
}

fun String.cellFromHex(): Cell {
    val parsed = bocFromHex()
    if (parsed.roots.size != 1) {
        throw IllegalArgumentException("Deserialized more than one cell")
    }
    return parsed.first()
}

fun Cell.toByteArray(): ByteArray {
    return BagOfCells(this).toByteArray()
}

fun Cell.base64(): String {
    return toByteArray().encodeBase64()
}

fun Cell.hex(): String {
    return hex(toByteArray())
}

fun CellSlice.loadRemainingBits(): BitString {
    return BitString((this.bitsPosition until this.bits.size).map { this.loadBit() })
}
