package com.tonapps.singer.core.account

import android.content.Context
import android.util.Log
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.core.Scope
import com.tonapps.singer.core.SecurityUtils
import com.tonapps.singer.core.SimpleState
import com.tonapps.singer.core.password.Password
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.mnemonic.Mnemonic

class AccountRepository(context: Context) {

    private val dataSource = AccountDataSource(context)
    private val _keysEntityFlow = MutableStateFlow<List<KeyEntity>?>(null)

    val keysEntityFlow = _keysEntityFlow.asStateFlow().filterNotNull()

    init {
        Scope.repositories.launch(Dispatchers.IO) {
            _keysEntityFlow.value = dataSource.getEntities()
        }
    }

    suspend fun deleteKey(id: Long) = withContext(Dispatchers.IO) {
        dataSource.delete(id)

        _keysEntityFlow.value = _keysEntityFlow.value?.filter { it.id != id }
    }

    fun findIdByPublicKey(publicKey: PublicKeyEd25519): Flow<Long> = flow {
        val id = dataSource.findIdByPublicKey(publicKey)
        emit(id)
    }.filterNotNull()

    suspend fun setName(id: Long, name: String) = withContext(Dispatchers.IO) {
        dataSource.setName(id, name)

        _keysEntityFlow.value = _keysEntityFlow.value?.map { entity ->
            if (entity.id == id) {
                entity.copy(name = name)
            } else {
                entity
            }
        }
    }

    fun getKey(id: Long): Flow<KeyEntity> {
        return keysEntityFlow.map { list ->
            list.firstOrNull { it.id == id }
        }.filterNotNull().distinctUntilChanged()
    }

    suspend fun getMnemonic(id: Long): List<String> {
        return dataSource.getMnemonic(id) ?: throw IllegalStateException("Mnemonic not found")
    }

    suspend fun getPrivateKey(id: Long): PrivateKeyEd25519 {
        val mnemonic = getMnemonic(id)
        val seed = Mnemonic.toSeed(mnemonic)
        return PrivateKeyEd25519(seed)
    }

    suspend fun sign(id: Long, unsignedBody: Cell): Cell = withContext(Dispatchers.IO) {
        val privateKey = getPrivateKey(id)
        val signature = BitString(privateKey.sign(unsignedBody.hash()))

        CellBuilder.createCell {
            storeBits(signature)
            storeBits(unsignedBody.bits)
            storeRefs(unsignedBody.refs)
        }
    }

    suspend fun addKey(name: String, mnemonic: List<String>) = withContext(Dispatchers.IO) {
        val seed = Mnemonic.toSeed(mnemonic)
        val publicKey = PrivateKeyEd25519(seed).publicKey()

        val keyId = System.currentTimeMillis()
        dataSource.setMnemonic(keyId, mnemonic)
        dataSource.setPublicKey(keyId, publicKey)
        dataSource.setName(keyId, name)
        dataSource.addId(keyId)

        val entity = KeyEntity(
            id = keyId,
            name = name,
            publicKey = publicKey,
        )

        val currentList = _keysEntityFlow.value ?: emptyList()
        _keysEntityFlow.value = currentList + entity
    }

    fun hasPassword(): Boolean {
        return dataSource.hasPassword()
    }

    suspend fun setPassword(password: String) = withContext(Dispatchers.IO) {
        val salt = SecurityUtils.randomBytes(16)
        val hash = SecurityUtils.argon2Hash(password, salt)
        dataSource.setPasswordData(salt, hash)
    }

    suspend fun checkPassword(password: String): Password.Result = withContext(Dispatchers.IO) {
        try {
            if (password.isBlank()) {
                return@withContext Password.Result.Error
            }
            val hash = dataSource.getPasswordHash() ?: return@withContext Password.Result.Error
            if (SecurityUtils.argon2Verify(password, hash)) {
                return@withContext Password.Result.Success
            }
            return@withContext Password.Result.Incorrect
        } catch (e: Throwable) {
            return@withContext Password.Result.Error
        }
    }

    fun checkPasswordFlow(password: String): Flow<SimpleState> = flow {
        emit(SimpleState.Loading)
        if (checkPassword(password) == Password.Result.Success) {
            emit(SimpleState.Success)
        } else {
            emit(SimpleState.Error)
        }
    }

}