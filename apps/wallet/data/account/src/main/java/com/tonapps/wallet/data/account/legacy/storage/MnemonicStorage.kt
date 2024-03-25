package com.tonapps.wallet.data.account.legacy.storage

import android.content.Context
import core.keyvalue.EncryptedKeyValue

internal class MnemonicStorage(context: Context) {

    private companion object {
        private const val WORDS_KEY = "words"
        private const val SEED_KEY = "seed"
    }

    private val encryptedKeyValue = EncryptedKeyValue(context, "tonkeeper")

    suspend fun getSeed(id: Long): ByteArray? {
        val key = keySeed(id)
        return encryptedKeyValue.getByteArray(key, null)
    }

    suspend fun setSeed(id: Long, seed: ByteArray) {
        val key = keySeed(id)
        encryptedKeyValue.putByteArray(key, seed)
    }

    suspend fun add(id: Long, mnemonic: List<String>) {
        val key = keyWords(id)
        if (mnemonic.isNotEmpty()) {
            encryptedKeyValue.putString(key, mnemonic.joinToString(","))
        }
    }

    suspend fun get(id: Long): List<String> {
        val key = keyWords(id)
        val words = encryptedKeyValue.getString(key, null)?.split(",")
        return words ?: emptyList()
    }

    suspend fun delete(id: Long) {
        val key = keyWords(id)
        encryptedKeyValue.remove(key)
    }

    suspend fun clearAll() {
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