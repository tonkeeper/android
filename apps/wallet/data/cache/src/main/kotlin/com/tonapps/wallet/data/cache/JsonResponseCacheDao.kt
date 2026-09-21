package com.tonapps.wallet.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface JsonResponseCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JsonResponseCacheEntity)

    @Query(
        """
        SELECT * FROM json_response_cache
        WHERE scope = :scope AND wallet_id = :walletId AND cache_key = :cacheKey
        LIMIT 1
        """,
    )
    suspend fun get(scope: String, walletId: String, cacheKey: String): JsonResponseCacheEntity?

    @Query("DELETE FROM json_response_cache WHERE wallet_id = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM json_response_cache WHERE wallet_id IN (:walletIds)")
    suspend fun deleteByWalletIds(walletIds: List<String>)
}
