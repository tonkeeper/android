package com.tonapps.wallet.data.multichain.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteAssetDao {

    @Query("SELECT asset_id FROM favorite_asset WHERE wallet_id = :walletId ORDER BY created_at ASC")
    fun observeIds(walletId: String): Flow<List<String>>

    @Query("SELECT asset_id FROM favorite_asset WHERE wallet_id = :walletId ORDER BY created_at ASC")
    suspend fun getAllIds(walletId: String): List<String>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM favorite_asset WHERE wallet_id = :walletId AND asset_id = :assetId)",
    )
    suspend fun isFavorite(walletId: String, assetId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteAssetEntity)

    @Query("DELETE FROM favorite_asset WHERE wallet_id = :walletId AND asset_id = :assetId")
    suspend fun delete(walletId: String, assetId: String)

    @Query("DELETE FROM favorite_asset WHERE wallet_id = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}
