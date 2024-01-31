package security.vault

import android.content.SharedPreferences
import security.Security
import security.atomicRead
import security.atomicWrite
import security.clear
import security.decrypt
import security.encrypt
import security.getByteArray
import security.putByteArray
import java.io.File
import javax.crypto.SecretKey

internal class Storage(
    private val prefs: SharedPreferences
) {

    private companion object {
        private const val IV_SIZE = 16

        fun wrapBodyKey(id: Long): String {
            return "item_body_$id"
        }

        fun wrapIvKey(id: Long): String {
            return "item_iv_$id"
        }
    }

    fun get(id: Long, secret: SecretKey): ByteArray {
        val iv = getIv(id) ?: throw IllegalStateException("iv is empty")
        val encrypted = getBody(id) ?: throw IllegalStateException("body is empty")
        val data = secret.decrypt(iv, encrypted)
        encrypted.clear()
        return data
    }

    fun put(secret: SecretKey, id: Long, data: ByteArray) {
        if (data.isEmpty()) {
            delete(id)
        } else {
            val iv = Security.randomBytes(IV_SIZE)
            val encrypted = secret.encrypt(iv, data)
            setIvAndBody(id, iv, encrypted)
            data.clear()
        }
    }

    private fun getIv(id: Long): ByteArray? {
        return prefs.getByteArray(wrapIvKey(id))
    }

    private fun getBody(id: Long): ByteArray? {
        return prefs.getByteArray(wrapBodyKey(id))
    }

    private fun delete(id: Long) {
        prefs.edit()
            .remove(wrapIvKey(id))
            .remove(wrapBodyKey(id))
            .apply()
    }

    private fun setIvAndBody(id: Long, iv: ByteArray, body: ByteArray) {
        prefs.edit()
            .putByteArray(wrapIvKey(id), iv)
            .putByteArray(wrapBodyKey(id), body)
            .apply()
    }
}