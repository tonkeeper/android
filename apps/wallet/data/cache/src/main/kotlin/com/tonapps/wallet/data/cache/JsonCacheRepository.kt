package com.tonapps.wallet.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JsonCacheRepository(
    private val dao: JsonResponseCacheDao,
) {
    suspend fun getJson(
        scope: JsonResponseCacheScope,
        cacheKey: String,
        walletId: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        dao.get(
            scope = scope.name,
            walletId = walletId.orEmpty(),
            cacheKey = cacheKey,
        )?.json
    }

    suspend fun save(
        scope: JsonResponseCacheScope,
        cacheKey: String,
        json: String,
        walletId: String? = null,
    ) = withContext(Dispatchers.IO) {
        dao.upsert(
            JsonResponseCacheEntity(
                scope = scope.name,
                walletId = walletId.orEmpty(),
                cacheKey = cacheKey,
                json = json,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteByWalletId(walletId: String) = withContext(Dispatchers.IO) {
        dao.deleteByWalletId(walletId)
    }

    suspend fun deleteByWalletIds(walletIds: List<String>) = withContext(Dispatchers.IO) {
        dao.deleteByWalletIds(walletIds)
    }
}
