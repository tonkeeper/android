package security

import android.content.SharedPreferences
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.Key

private val DIGITS = "0123456789abcdef".toCharArray()

fun CharArray.clear() {
    this.fill('0')
}

fun ByteArray.clear() {
    this.fill(0)
}

fun tryCallGC() {
    try {
        System.gc()
    } catch (ignored: Throwable) {}
}

fun clearWithGC(vararg arrays: ByteArray?) {
    arrays.forEach { it?.clear() }
    tryCallGC()
}

fun Key.encrypt(iv: ByteArray, data: ByteArray): ByteArray {
    return CipherAes.encrypt(this, iv, data)
}

fun Key.decrypt(iv: ByteArray, data: ByteArray): ByteArray {
    return CipherAes.decrypt(this, iv, data)
}

fun File.asAtomicFile(): AtomicFile {
    return AtomicFile(this)
}

fun AtomicFile.write(data: ByteArray) {
    val stream = startWrite()
    try {
        stream.write(data)
    } catch (e: Exception) {
        failWrite(stream)
        throw e
    } finally {
        finishWrite(stream)
    }
}

fun File.atomicWrite(data: ByteArray) {
    asAtomicFile().write(data)
}

fun File.atomicRead(): ByteArray {
    return asAtomicFile().readFully()
}

fun SharedPreferences.getByteArray(key: String): ByteArray? {
    val value = getString(key, null)
    if (value.isNullOrBlank()) {
        return null
    }
    return Base64.decode(value, Base64.DEFAULT)
}

fun SharedPreferences.Editor.putByteArray(key: String, value: ByteArray): SharedPreferences.Editor {
    putString(key, Base64.encodeToString(value, Base64.DEFAULT))
    return this
}

fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    bytes.forEach { byte ->
        val b = byte.toInt() and 0xFF
        append(DIGITS[b shr 4])
        append(DIGITS[b and 0x0F])
    }
}