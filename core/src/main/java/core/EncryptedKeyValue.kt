package core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import core.extensions.toBase64
import core.extensions.toByteArrayFromBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EncryptedKeyValue(
    context: Context,
    name: String
) {

    private val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
    private val mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

    private val preferences = EncryptedSharedPreferences.create(
        name,
        mainKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun getString(
        key: String,
        defValue: String? = null
    ): String? = withContext(Dispatchers.IO) {
        return@withContext preferences.getString(key, defValue)
    }

    suspend fun putString(
        key: String,
        value: String?
    ) = withContext(Dispatchers.IO) {
        with (preferences.edit()) {
            putString(key, value)
            apply()
        }
    }

    suspend fun putInt(
        key: String,
        value: Int
    ) = withContext(Dispatchers.IO) {
        with (preferences.edit()) {
            putInt(key, value)
            apply()
        }
    }

    suspend fun getInt(
        key: String,
        defValue: Int = 0
    ): Int = withContext(Dispatchers.IO) {
        return@withContext preferences.getInt(key, defValue)
    }

    suspend fun putBoolean(
        key: String,
        value: Boolean
    ) = withContext(Dispatchers.IO) {
        with (preferences.edit()) {
            putBoolean(key, value)
            apply()
        }
    }

    suspend fun getBoolean(
        key: String,
        defValue: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext preferences.getBoolean(key, defValue)
    }

    suspend fun remove(
        key: String
    ) = withContext(Dispatchers.IO) {
        with (preferences.edit()) {
            remove(key)
            apply()
        }
    }

    suspend fun getByteArray(
        key: String,
        defValue: ByteArray? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext preferences.getString(key, null)?.toByteArrayFromBase64() ?: defValue
    }

    suspend fun putByteArray(
        key: String,
        value: ByteArray?
    ) = withContext(Dispatchers.IO) {
        with (preferences.edit()) {
            putString(key, value?.toBase64())
            apply()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        with (preferences.edit()) {
            clear()
            apply()
        }
    }

}