package com.tonapps.blockchain.ton.extensions

import org.json.JSONObject
import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.crypto.hex
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun String.toBoc(): BagOfCells {
    /*if (startsWith("{")) { // oh fuck....
        return toBocFromJSBuffer()
    }*/
    return try {
        /*val bytes = Base64.Default.Mime.decode(this)
        return BagOfCells(bytes)*/
        val fixedBoc = this.replace("-", "+")
            .replace("_", "/")
        BagOfCells(fixedBoc.base64())
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

@OptIn(ExperimentalEncodingApi::class)
fun Cell.base64(): String {
    /*val bytes = toByteArray()
    return Base64.Default.Mime.encode(bytes)*/
    return org.ton.crypto.base64(toByteArray())
}

fun Cell.hex(): String {
    return hex(toByteArray())
}

fun CellSlice.loadRemainingBits(): BitString {
    return BitString((this.bitsPosition until this.bits.size).map { this.loadBit() })
}
