package com.tonkeeper.api.base

import com.tonkeeper.api.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

abstract class BaseAccountRepository<Item> {

    private val memory = ConcurrentHashMap<String, List<Item>>(100, 1.0f, 2)

    suspend fun getFromCloud(accountId: String): RepositoryResponse<List<Item>>? {
        val account = fromCloud(accountId) ?: return null
        return RepositoryResponse.cloud(account)
    }

    open suspend fun get(
        accountId: String
    ): RepositoryResponse<List<Item>>? = withContext(Dispatchers.IO) {
        try {
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
        } catch (e: Throwable) {
            return@withContext null
        }
    }

    private fun fromMemory(accountId: String): List<Item>? {
        return memory[accountId]
    }

    private suspend fun fromCache(
        accountId: String
    ): List<Item>? {
        val items = onCacheRequest(accountId)
        if (items.isEmpty()) {
            return null
        }
        insertMemory(accountId, items)
        return items
    }

    private suspend fun fromCloud(
        accountId: String
    ): List<Item>? {
        val items = fetch(accountId) ?: return null
        insertMemory(accountId, items)
        deleteCache(accountId)
        insertCache(accountId, items)
        return items
    }

    private fun insertMemory(accountId: String, items: List<Item>) {
        memory[accountId] = items.toList()
    }

    private suspend fun fetch(
        accountId: String
    ): List<Item>? = withContext(Dispatchers.IO) {
        withRetry {
            onFetchRequest(accountId)
        }
    }

    abstract suspend fun insertCache(accountId: String, items: List<Item>)

    abstract suspend fun deleteCache(accountId: String)

    abstract suspend fun onCacheRequest(accountId: String): List<Item>

    abstract fun find(value: String, items: List<Item>): Item?

    abstract fun onFetchRequest(accountId: String): List<Item>

}