package com.tonapps.security

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import com.tonapps.security.spec.SimpleSecretSpec
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

// source https://android.googlesource.com/platform/libcore/+/master/support/src/test/java/libcore/java/lang/ref/FinalizationTester.java
fun tryCallGC() {
    Runtime.getRuntime().gc()
    try {
        Thread.sleep(100)
    } catch (ignored: Throwable) {}
    System.runFinalization()
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

fun String.hex(): ByteArray {
    val len = length
    if (len % 2 != 0) {
        throw IllegalArgumentException("Invalid hex string")
    }
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

fun Context.securePrefs(name: String): SharedPreferences {
    KeyHelper.createIfNotExists(name)

    return EncryptedSharedPreferences.create(
        name,
        name,
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
