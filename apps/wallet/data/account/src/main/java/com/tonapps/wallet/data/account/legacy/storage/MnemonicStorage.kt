package com.tonapps.wallet.data.account.legacy.storage

import android.content.Context
import com.tonapps.extensions.clear
import com.tonapps.extensions.getByteArray
import com.tonapps.extensions.prefsEncrypted
import com.tonapps.extensions.putByteArray
import com.tonapps.extensions.putString
import com.tonapps.extensions.remove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MnemonicStorage(context: Context) {

    private companion object {
        private const val WORDS_KEY = "words"
        private const val SEED_KEY = "seed"
    }

    private val encryptedKeyValue = context.prefsEncrypted("tonkeeper")

    suspend fun getSeed(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        val key = keySeed(id)
        encryptedKeyValue.getByteArray(key)
    }

    suspend fun setSeed(id: Long, seed: ByteArray) = withContext(Dispatchers.IO) {
        val key = keySeed(id)
        encryptedKeyValue.putByteArray(key, seed)
    }

    suspend fun add(id: Long, mnemonic: List<String>) = withContext(Dispatchers.IO) {
        val key = keyWords(id)
        if (mnemonic.isNotEmpty()) {
            encryptedKeyValue.putString(key, mnemonic.joinToString(","))
        }
    }

    suspend fun get(id: Long): List<String> = withContext(Dispatchers.IO) {
        val key = keyWords(id)
        val words = encryptedKeyValue.getString(key, null)?.split(",")
        words ?: emptyList()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val key = keyWords(id)
        encryptedKeyValue.remove(key)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        encryptedKeyValue.clear()
    }

    private fun keyWords(id: Long): String {
        if (id == 0L) {
            return WORDS_KEY
        }
        return "${WORDS_KEY}_$id"
    }

    private fun keySeed(id: Long): String {
        if (id == 0L) {
            return SEED_KEY
        }
        return "${SEED_KEY}_$id"
    }
}