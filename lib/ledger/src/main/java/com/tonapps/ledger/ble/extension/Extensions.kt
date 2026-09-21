package com.tonapps.ledger.ble.extension

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.tonapps.blockchain.utils.remove0x
import java.nio.ByteBuffer
import java.util.*

fun String.toUUID(): UUID = UUID.fromString(this)

fun String.fromHexStringToBytes(): ByteArray {
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun ByteArray.toHexString(): String = mapIndexed { _, byte ->
    byte.toHexString()
}.joinToString(separator = "")

fun Byte.toHexString(needZeroPadding: Boolean = true): String =
    if (needZeroPadding) {
        "%02x".format(this)
    } else {
        "%2x".format(this).trim()
    }

fun String.hexAsUtf8(): String? {
    if (!startsWith("0x")) {
        return null
    }

    val clean = remove0x()

    if (clean.length % 2 != 0) {
        return null
    }

    return try {
        val bytes = ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2)
                .toInt(16)
                .toByte()
        }

        val decoder = Charsets.UTF_8.newDecoder()
        return decoder
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: Exception) {
        null
    }
}

fun Int.toHexString(): String {
    val buffer = ByteArray(4) // 4 * 8 => 32, Int size
    buffer[0] = (this shr 24).toByte()
    buffer[1] = (this shr 16).toByte()
    buffer[2] = (this shr 8).toByte()
    buffer[3] = (this shr 0).toByte()

    return buffer.toHexString()
}


fun Context.bluetoothSupported(): Boolean =
    this.getSystemService(BluetoothManager::class.java).adapter != null

@SuppressLint("MissingPermission")
fun Context.isBluetoothEnabled(): Boolean = this.bluetoothSupported()
        && this.getSystemService(BluetoothManager::class.java).adapter.enable()
