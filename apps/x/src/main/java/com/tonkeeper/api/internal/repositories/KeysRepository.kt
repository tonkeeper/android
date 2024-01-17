package com.tonkeeper.api.internal.repositories

import android.content.Context
import com.tonkeeper.api.base.BaseBlobRepository
import com.tonkeeper.api.internal.Tonkeeper
import com.tonkeeper.api.internal.TonkeeperKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeysRepository(context: Context): BaseBlobRepository<TonkeeperKeys>("keys", context) {

    suspend fun getValue(key: String): String? {
        val data = get()
        return data.variables[key]
    }

    suspend fun get(): TonkeeperKeys {
        val cache = fromCache()
        if (cache != null) {
            return cache
        }
        return fromCloud()
    }

    private suspend fun fromCloud(): TonkeeperKeys = withContext(Dispatchers.IO) {
        val json = Tonkeeper.get("keys")
        saveCache(
            blob = json.toString()
        )

        val data = TonkeeperKeys(json)
        setMemory(
            data = data
        )
        return@withContext data
    }

    override fun onParse(blob: String): TonkeeperKeys {
        return TonkeeperKeys(blob)
    }

}