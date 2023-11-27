package com.tonkeeper.api.account

import androidx.collection.ArrayMap
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.account.db.AccountDao
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

    suspend fun clear(accountId: String) {
        memory.clear()
        dao.delete(accountId)
    }

    private fun fromMemory(accountId: String): Account? {
        return memory[accountId]
    }

    private suspend fun fromCache(accountId: String): Account? {
        val account = dao.get(accountId) ?: return null
        memory[accountId] = account
        return account
    }

    private suspend fun fromCloud(accountId: String): Account {
        val account = fetch(accountId)
        dao.insert(accountId, account)
        memory[accountId] = account
        return account
    }

    private suspend fun fetch(accountId: String): Account {
        return withRetry { api.getAccount(accountId) }
    }

    suspend fun get(accountId: String): Account = withContext(Dispatchers.IO) {
        val memory = fromMemory(accountId)
        if (memory != null) {
            return@withContext memory
        }
        val cache = fromCache(accountId)
        if (cache != null) {
            return@withContext cache
        }
        return@withContext fromCloud(accountId)
    }
}