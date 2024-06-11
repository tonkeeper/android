package com.tonapps.tonkeeper.api.base

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

abstract class BaseBlobRepository<Data>(
    private val name: String,
    private val context: Context,
) {

    private val cacheFolder: File
        get() {
            val file = File(context.cacheDir, "blob")
            if (!file.exists()) {
                file.mkdirs()
            }
            return file
        }

    private val memory = ConcurrentHashMap<String, Data>(100, 1.0f, 2)

    suspend fun fromCache(
        accountId: String = "global",
        testnet: Boolean = false,
    ): Data? {
        val accountKey = AccountKey(accountId, testnet)
        return fromCache(accountKey)
    }

    suspend fun fromCache(
        accountKey: AccountKey,
    ): Data? = withContext(Dispatchers.IO) {
        val memory = getFromMemory(accountKey)
        if (memory != null) {
            return@withContext memory
        }

        val file = getFile(accountKey)
        if (!file.exists()) return@withContext null
        try {
            val blob = file.readText()
            val response = onParse(blob)
            setMemory(accountKey, response)
            return@withContext response
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun saveCache(
        accountId: String = "global",
        testnet: Boolean = false,
        blob: String
    ) = withContext(Dispatchers.IO) {
        val accountKey = AccountKey(accountId, testnet)
        val file = getFile(accountKey)
        file.writeText(blob)
    }

    private fun getFile(
        accountKey: AccountKey,
    ): File {
        val key = accountKey.toString()
        val folder = File(cacheFolder, key)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, "${name}.json")
    }

    fun setMemory(
        accountId: String = "global",
        testnet: Boolean = false,
        data: Data
    ) {
        val accountKey = AccountKey(accountId, testnet)
        setMemory(accountKey, data)
    }

    fun setMemory(accountKey: AccountKey, data: Data) {
        memory[accountKey.toString()] = data
    }

    private fun getFromMemory(accountKey: AccountKey): Data? {
        return memory[accountKey.toString()]
    }

    abstract fun onParse(blob: String): Data
}