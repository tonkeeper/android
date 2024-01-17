package com.tonkeeper.api.account

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.account.db.AccountDao
import com.tonkeeper.api.base.AccountKey
import com.tonkeeper.api.base.RepositoryResponse
import com.tonkeeper.api.base.SourceAPI
import com.tonkeeper.api.withRetry
import io.tonapi.apis.AccountsApi
import io.tonapi.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AccountRepository(
    private val api: SourceAPI<AccountsApi> = Tonapi.accounts,
    private val dao: AccountDao = App.db.accountDao()
) {

    private val memory = ConcurrentHashMap<String, Account>()

    private fun fromMemory(accountKey: AccountKey): Account? {
        return memory[accountKey.toString()]
    }

    private suspend fun fromCache(accountKey: AccountKey): Account? {
        val key = accountKey.toString()
        val account = dao.get(key) ?: return null
        memory[key] = account
        return account
    }

    private suspend fun fromCloud(accountId: String, testnet: Boolean): Account? {
        val account = fetch(accountId, testnet) ?: return null

        val key = AccountKey(accountId, testnet).toString()
        dao.insert(key, account)
        memory[key] = account
        return account
    }

    private suspend fun fetch(
        accountId: String,
        testnet: Boolean,
    ): Account? = withContext(Dispatchers.IO) {
        withRetry { api.get(testnet).getAccount(accountId) }
    }

    suspend fun getFromCloud(accountId: String, testnet: Boolean): RepositoryResponse<Account>? {
        val account = fromCloud(accountId, testnet) ?: return null
        return RepositoryResponse.cloud(account)
    }

    suspend fun get(accountId: String, testnet: Boolean): RepositoryResponse<Account>? = withContext(Dispatchers.IO) {
        val accountKey = AccountKey(accountId, testnet)
        val memory = fromMemory(accountKey)
        if (memory != null) {
            return@withContext RepositoryResponse.memory(memory)
        }
        val cache = fromCache(accountKey)
        if (cache != null) {
            return@withContext RepositoryResponse.cache(cache)
        }

        val cloud = fromCloud(accountId, testnet) ?: return@withContext null

        return@withContext RepositoryResponse.cloud(cloud)
    }
}