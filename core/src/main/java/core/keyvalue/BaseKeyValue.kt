package core.keyvalue

import android.content.SharedPreferences
import android.os.Parcelable
import core.extensions.toBase64
import core.extensions.toByteArray
import core.extensions.toByteArrayFromBase64
import core.extensions.toParcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class BaseKeyValue {

    abstract val preferences: SharedPreferences

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

    suspend fun getLong(
        key: String,
        defValue: Long = 0L
    ): Long = withContext(Dispatchers.IO) {
        return@withContext preferences.getLong(key, defValue)
    }

    suspend fun putLong(
        key: String,
        value: Long
    ) = withContext(Dispatchers.IO) {
        with (preferences.edit()) {
            putLong(key, value)
            apply()
        }
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

    suspend fun putParcelable(
        key: String,
        value: Parcelable?
    ) = withContext(Dispatchers.IO) {
        putByteArray(key, value?.toByteArray())
    }

    suspend fun <T: Parcelable> getParcelable(
        key: String,
        creator: Parcelable.Creator<T>,
        defValue: T? = null
    ): T? = withContext(Dispatchers.IO) {
        try {
            return@withContext getByteArray(key, null)?.toParcel(creator) ?: defValue
        } catch (e: Throwable) {
            return@withContext defValue
        }
    }

    suspend fun getLongArray(key: String): LongArray {
        val string = getString(key, null) ?: return LongArray(0)
        return string.split(",").map { it.toLong() }.toLongArray()
    }

    suspend fun putLongArray(key: String, value: LongArray) {
        putString(key, value.joinToString(","))
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        with (preferences.edit()) {
            clear()
            apply()
        }
    }
}