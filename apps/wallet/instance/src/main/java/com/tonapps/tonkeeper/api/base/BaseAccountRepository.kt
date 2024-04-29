package com.tonapps.tonkeeper.api.base

import com.tonapps.tonkeeper.api.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

abstract class BaseAccountRepository<Item> {

    private val memory = ConcurrentHashMap<String, List<Item>>(100, 1.0f, 2)

    suspend fun getFromCloud(
        accountId: String,
        testnet: Boolean
    ): RepositoryResponse<List<Item>>? {
        val account = fromCloud(accountId, testnet) ?: return null
        return RepositoryResponse.cloud(account)
    }

    open suspend fun get(
        accountId: String,
        testnet: Boolean
    ): RepositoryResponse<List<Item>>? = withContext(Dispatchers.IO) {
        val accountKey = AccountKey(accountId, testnet)
        try {
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
        } catch (e: Throwable) {
            return@withContext null
        }
    }

    private fun fromMemory(
        accountKey: AccountKey
    ): List<Item>? {
        return memory[accountKey.toString()]
    }

    private suspend fun fromCache(
        accountKey: AccountKey
    ): List<Item>? {
        val items = onCacheRequest(accountKey)
        if (items.isEmpty()) {
            return null
        }
        insertMemory(accountKey, items)
        return items
    }

    private suspend fun fromCloud(
        accountId: String,
        testnet: Boolean
    ): List<Item>? {
        val items = fetch(accountId, testnet) ?: return null
        val accountKey = AccountKey(accountId, testnet)
        insertMemory(accountKey, items)
        deleteCache(accountKey)
        insertCache(accountKey, items)
        return items
    }

    private fun insertMemory(
        accountKey: AccountKey,
        items: List<Item>
    ) {
        memory[accountKey.toString()] = items.toList()
    }

    private suspend fun fetch(
        accountId: String,
        testnet: Boolean
    ): List<Item>? = withContext(Dispatchers.IO) {
        withRetry {
            onFetchRequest(accountId, testnet)
        }
    }

    abstract suspend fun insertCache(accountKey: AccountKey, items: List<Item>)

    abstract suspend fun deleteCache(accountKey: AccountKey)

    abstract suspend fun onCacheRequest(accountKey: AccountKey): List<Item>

    abstract fun find(value: String, items: List<Item>): Item?

    abstract fun onFetchRequest(accountId: String, testnet: Boolean): List<Item>

}