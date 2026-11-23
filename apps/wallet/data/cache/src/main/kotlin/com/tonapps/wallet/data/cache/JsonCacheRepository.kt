package com.tonapps.wallet.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    fun observeJson(
        scope: JsonResponseCacheScope,
        cacheKey: String,
        walletId: String? = null,
    ): Flow<String?> = dao.observeJson(
        scope = scope.name,
        walletId = walletId.orEmpty(),
        cacheKey = cacheKey,
    ).distinctUntilChanged()

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
}
