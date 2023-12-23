package com.tonkeeper.api.account

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.account.db.AccountDao
import com.tonkeeper.api.base.RepositoryResponse
import com.tonkeeper.api.withRetry
import io.tonapi.apis.AccountsApi
import io.tonapi.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AccountRepository(
    private val api: AccountsApi = Tonapi.accounts,
    private val dao: AccountDao = App.db.accountDao()
) {

    private val memory = ConcurrentHashMap<String, Account>()

    private fun fromMemory(accountId: String): Account? {
        return memory[accountId]
    }

    private suspend fun fromCache(accountId: String): Account? {
        val account = dao.get(accountId) ?: return null
        memory[accountId] = account
        return account
    }

    private suspend fun fromCloud(accountId: String): Account? {
        val account = fetch(accountId) ?: return null
        dao.insert(accountId, account)
        memory[accountId] = account
        return account
    }

    private suspend fun fetch(
        accountId: String
    ): Account? = withContext(Dispatchers.IO) {
        withRetry { api.getAccount(accountId) }
    }

    suspend fun getFromCloud(accountId: String): RepositoryResponse<Account>? {
        val account = fromCloud(accountId) ?: return null
        return RepositoryResponse.cloud(account)
    }

    suspend fun get(accountId: String): RepositoryResponse<Account>? = withContext(Dispatchers.IO) {
        val memory = fromMemory(accountId)
        if (memory != null) {
            return@withContext RepositoryResponse.memory(memory)
        }
        val cache = fromCache(accountId)
        if (cache != null) {
            return@withContext RepositoryResponse.cache(cache)
        }

        val cloud = fromCloud(accountId) ?: return@withContext null

        return@withContext RepositoryResponse.cloud(cloud)
    }
}