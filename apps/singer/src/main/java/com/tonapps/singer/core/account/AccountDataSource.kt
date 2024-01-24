package com.tonapps.singer.core.account

import android.content.Context
import android.util.Log
import com.lambdapioneer.argon2kt.Argon2Kt
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.core.SecurityUtils
import core.keyvalue.EncryptedKeyValue
import core.keyvalue.KeyValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519

class AccountDataSource(
    context: Context,
) {

    private companion object {
        private const val STORAGE_NAME = "account"
        private const val IDS_KEY = "ids"
        private const val PASSWORD_SALT_KEY = "password_salt"
        private const val PASSWORD_HASH_KEY = "password_hash"
        private const val MNEMONIC_KEY = "mnemonic"
    }

    private val keyValue = KeyValue(context, STORAGE_NAME)
    private val secureKeyValue = EncryptedKeyValue(context, STORAGE_NAME)

    fun hasPassword(): Boolean {
        return secureKeyValue.contains(PASSWORD_HASH_KEY)
    }

    suspend fun setPasswordData(salt: ByteArray, hash: String) {
        secureKeyValue.putByteArray(PASSWORD_SALT_KEY, salt)
        secureKeyValue.putString(PASSWORD_HASH_KEY, hash)
    }

    suspend fun getPasswordHash(): String? {
        return secureKeyValue.getString(PASSWORD_HASH_KEY)
    }

    suspend fun findIdByPublicKey(publicKey: PublicKeyEd25519): Long? = withContext(Dispatchers.IO) {
        getIds().firstOrNull { id ->
            getPublicKey(id)?.key == publicKey.key
        }
    }

    suspend fun getEntities(): List<KeyEntity> {
        return getIds().map {
            getEntity(it)
        }.filterNotNull()
    }

    suspend fun setMnemonic(id: Long, mnemonic: List<String>) {
        val mnemonicString = mnemonic.joinToString(",")
        secureKeyValue.putString(keyMnemonic(id), mnemonicString)
    }

    suspend fun getMnemonic(id: Long): List<String>? {
        return secureKeyValue.getString(keyMnemonic(id))?.split(",")
    }

    private suspend fun getEntity(id: Long): KeyEntity? {
        val name = getName(id) ?: return null
        val publicKey = getPublicKey(id) ?: return null

        return KeyEntity(id, name, publicKey)
    }

    suspend fun addId(id: Long) {
        val ids = getIds().toMutableList()
        ids.add(id)
        setIds(ids.toLongArray())
    }

    private suspend fun removeId(id: Long) {
        val ids = getIds().toMutableList()
        ids.remove(id)
        setIds(ids.toLongArray())
    }

    suspend fun delete(id: Long) {
        removeId(id)

        keyValue.remove(keyName(id))
        keyValue.remove(keyPublicKey(id))
        secureKeyValue.remove(keyMnemonic(id))
    }

    private suspend fun setIds(ids: LongArray) {
        keyValue.putLongArray(IDS_KEY, ids)
    }

    private suspend fun getIds(): LongArray {
        val ids = keyValue.getLongArray(IDS_KEY).filter { it > 0 }
        return ids.toLongArray()
    }

    private suspend fun getName(id: Long): String? {
        return keyValue.getString(keyName(id))
    }

    suspend fun setName(id: Long, name: String) {
        keyValue.putString(keyName(id), name)
    }

    private suspend fun getPublicKey(id: Long): PublicKeyEd25519? {
        return keyValue.getByteArray(keyPublicKey(id))?.let { PublicKeyEd25519(it) }
    }

    suspend fun setPublicKey(id: Long, publicKey: PublicKeyEd25519) {
        keyValue.putByteArray(keyPublicKey(id), publicKey.key.toByteArray())
    }

    private fun keyName(id: Long) = "name_$id"

    private fun keyPublicKey(id: Long) = "public_key_$id"

    private fun keyMnemonic(id: Long) = "mnemonic_$id"

}