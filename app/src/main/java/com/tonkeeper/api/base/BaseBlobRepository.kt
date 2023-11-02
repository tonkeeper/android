package com.tonkeeper.api.base

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

abstract class BaseBlobRepository<Data>(
    private val name: String,
    private val context: Context
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
        accountId: String
    ): Data? = withContext(Dispatchers.IO) {
        val file = getFile(accountId)
        if (!file.exists()) return@withContext null
        val blob = file.readText()
        val response = onParse(blob)
        setMemory(accountId, response)
        return@withContext response
    }

    suspend fun saveCache(
        accountId: String,
        blob: String
    ) = withContext(Dispatchers.IO) {
        val file = getFile(accountId)
        file.writeText(blob)
    }

    fun getFile(
        accountId: String
    ): File {
        val folder = File(cacheFolder, accountId)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, "${name}.json")
    }

    fun setMemory(accountId: String, data: Data) {
        memory[accountId] = data
    }

    fun getFromMemory(accountId: String): Data? {
        return memory[accountId]
    }

    abstract fun onParse(blob: String): Data
}