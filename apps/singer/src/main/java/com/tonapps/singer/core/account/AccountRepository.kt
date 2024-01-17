package com.tonapps.singer.core.account

import android.content.Context
import android.os.SystemClock
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.core.Scope
import com.tonapps.singer.core.SecurityUtils
import com.tonapps.singer.core.flow.Resource
import com.tonapps.singer.core.flow.mutableResourceFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

class AccountRepository(context: Context) {

    private val dataSource = AccountDataSource(context)
    private val _keysEntityFlow = mutableResourceFlow<List<KeyEntity>>()

    val keysEntityFlow = _keysEntityFlow.asStateFlow()

    init {
        Scope.repositories.launch { updateKeysEntity()  }
    }

    private suspend fun updateKeysEntity() {
        _keysEntityFlow.value = Resource.Success(dataSource.getEntities())
    }

    suspend fun addKey(name: String, mnemonic: List<String>) {
        val seed = Mnemonic.toSeed(mnemonic)
        val publicKey = PrivateKeyEd25519(seed).publicKey()

        val keyId = System.currentTimeMillis()
        dataSource.setMnemonic(keyId, mnemonic)
        dataSource.setPublicKey(keyId, publicKey)
        dataSource.setName(keyId, name)
        dataSource.addId(keyId)

        updateKeysEntity()
    }

    fun hasPassword(): Boolean {
        return dataSource.hasPassword()
    }

    suspend fun setPassword(password: String) = withContext(Dispatchers.IO) {
        val salt = SecurityUtils.randomBytes(16)
        val hash = SecurityUtils.argon2Hash(password, salt)
        dataSource.setPasswordData(salt, hash)
    }

    suspend fun checkPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        val hash = dataSource.getPasswordHash() ?: return@withContext false
        SecurityUtils.argon2Verify(password, hash)
    }

}