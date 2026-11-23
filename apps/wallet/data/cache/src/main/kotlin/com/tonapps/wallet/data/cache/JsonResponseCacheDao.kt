package com.tonapps.wallet.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    @Query(
        """
        SELECT json FROM json_response_cache
        WHERE scope = :scope AND wallet_id = :walletId AND cache_key = :cacheKey
        LIMIT 1
        """,
    )
    fun observeJson(scope: String, walletId: String, cacheKey: String): Flow<String?>

    @Query("DELETE FROM json_response_cache WHERE wallet_id = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}
