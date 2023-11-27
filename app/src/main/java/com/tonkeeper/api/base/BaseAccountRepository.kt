package com.tonkeeper.api.base

import android.util.Log
import com.tonkeeper.api.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

abstract class BaseAccountRepository<Item> {

    private val memory = ConcurrentHashMap<String, List<Item>>(100, 1.0f, 2)

    suspend fun clear(accountId: String) {
        memory.remove(accountId)
        clearCache(accountId)
    }

    suspend fun getSingle(accountId: String, value: String): Item? {
        val items = get(accountId)
        return find(value, items)
    }

    suspend fun get(
        accountId: String
    ): List<Item> = withContext(Dispatchers.IO) {
        try {
            val memory = getFromMemory(accountId)
            if (memory != null) {
                return@withContext memory
            }
            val cache = getFromCache(accountId)

            if (cache != null) {
                return@withContext cache
            }

            return@withContext getFromNetwork(accountId)
        } catch (e: Throwable) {
            return@withContext emptyList<Item>()
        }
    }

    private fun getFromMemory(accountId: String): List<Item>? {
        return memory[accountId]
    }

    private suspend fun getFromCache(
        accountId: String
    ): List<Item>? {
        val items = fromCache(accountId)
        if (items.isEmpty()) {
            return null
        }
        insertMemory(accountId, items)
        return items
    }

    private suspend fun getFromNetwork(
        accountId: String
    ): List<Item> {
        val items = fetch(accountId)
        insertCache(accountId, items)
        insertMemory(accountId, items)
        return items
    }

    private fun insertMemory(accountId: String, items: List<Item>) {
        memory[accountId] = items.toList()
    }

    private suspend fun fetch(
        accountId: String
    ): List<Item> {
        return withRetry {
            fromCloud(accountId)
        }
    }

    abstract suspend fun insertCache(accountId: String, items: List<Item>)

    abstract suspend fun fromCache(accountId: String): List<Item>

    abstract fun find(value: String, items: List<Item>): Item?

    abstract fun fromCloud(accountId: String): List<Item>

    abstract suspend fun clearCache(accountId: String)

}