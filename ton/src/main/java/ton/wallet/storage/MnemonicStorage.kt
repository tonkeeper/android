package ton.wallet.storage

import android.content.Context
import android.util.Log
import core.keyvalue.EncryptedKeyValue

internal class MnemonicStorage(context: Context) {

    private companion object {
        private const val WORDS_KEY = "words"
    }

    private val encryptedKeyValue = EncryptedKeyValue(context, "tonkeeper")

    suspend fun add(id: Long, mnemonic: List<String>) {
        val key = key(id)
        if (mnemonic.isNotEmpty()) {
            encryptedKeyValue.putString(key, mnemonic.joinToString(","))
        }
    }

    suspend fun get(id: Long): List<String> {
        val key = key(id)
        val words = encryptedKeyValue.getString(key, null)?.split(",")
        return words ?: emptyList()
    }

    suspend fun delete(id: Long) {
        val key = key(id)
        encryptedKeyValue.remove(key)
    }

    suspend fun clearAll() {
        encryptedKeyValue.clear()
    }

    private fun key(id: Long): String {
        if (id == 0L) {
            return WORDS_KEY
        }
        return "${WORDS_KEY}_$id"
    }
}