package security

import android.content.SharedPreferences
import android.util.AtomicFile
import android.util.Base64
import security.spec.SimpleSecretSpec
import java.io.Closeable
import java.io.File
import java.security.Key
import javax.crypto.SecretKey

private val DIGITS = "0123456789abcdef".toCharArray()

fun CharArray.clear() {
    this.fill('0')
}

fun ByteArray.clear() {
    this.fill(0)
}

fun ByteArray.isZero(): Boolean {
    if (isEmpty()) {
        return true
    }

    for (b in this) {
        if (b != 0.toByte()) {
            return false
        }
    }
    return true
}

fun clear(vararg arrays: ByteArray?) {
    arrays.forEach { it?.clear() }
}

fun SecretKey.safeDestroy() {
    if (this is Closeable) {
        this.close()
    } else {
        try {
            destroy()
        } catch (ignored: Throwable) {
            // NoSuchMethodError: No interface method destroy()V in class Ljavax/crypto/SecretKey; or its super classes (declaration of 'javax.crypto.SecretKey' appears in /system/framework/core-oj.jar)
        }
    }
}

fun tryCallGC() {
    try {
        System.gc()
    } catch (ignored: Throwable) {}
}

fun Key.encrypt(iv: ByteArray, data: ByteArray): ByteArray? {
    return runCatching {
        CipherAes.encrypt(this, iv, data)
    }.getOrNull()
}

fun Key.decrypt(iv: ByteArray, data: ByteArray): ByteArray? {
    return runCatching {
        CipherAes.decrypt(this, iv, data)
    }.getOrNull()
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

fun base64(input: String): ByteArray? {
    return try {
        Base64.decode(input, Base64.DEFAULT)
    } catch (e: Throwable) {
        null
    }
}

fun base64(input: ByteArray): String? {
    return try {
        Base64.encodeToString(input, Base64.DEFAULT)
    } catch (e: Throwable) {
        null
    }
}

fun SharedPreferences.getByteArray(key: String): ByteArray? {
    val value = run {
        val value = getString(key, null)
        if (value.isNullOrBlank()) {
            return byteArrayOf(0)
        }
        base64(value) ?: byteArrayOf(0)
    }

    if (value.isZero()) {
        value.clear()
        return null
    }
    return value
}

fun SharedPreferences.Editor.putByteArray(key: String, value: ByteArray): SharedPreferences.Editor {
    if (value.isZero()) {
        remove(key)
        return this
    } else {
        val string = base64(value)
        if (string == null) {
            remove(key)
        } else {
            putString(key, string)
        }
    }
    return this
}

fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    bytes.forEach { byte ->
        val b = byte.toInt() and 0xFF
        append(DIGITS[b shr 4])
        append(DIGITS[b and 0x0F])
    }
}
