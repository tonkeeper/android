package com.tonapps.blockchain.ton.extensions

import com.tonapps.base64.decodeBase64
import com.tonapps.base64.encodeBase64
import org.json.JSONObject
import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.crypto.hex

fun String.toBoc(): BagOfCells {
    if (startsWith("{")) { // oh fuck....
        return toBocFromJSBuffer()
    }
    return try {
        BagOfCells(decodeBase64())
    } catch (e: Throwable) {
        BagOfCells(hex(this))
    }
}

private fun String.toBocFromJSBuffer(): BagOfCells {
    val json = JSONObject(this)
    val data = json.getJSONArray("data")
    val byteArray = ByteArray(data.length())
    for (i in 0 until data.length()) {
        byteArray[i] = data.getInt(i).toByte()
    }
    return BagOfCells(byteArray)
}

fun String.parseCell(): Cell {
    return toBoc().first()
}

fun String.safeParseCell(): Cell? {
    if (this.isBlank()) {
        return null
    }
    return try {
        parseCell()
    } catch (e: Throwable) {
        null
    }
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
