package com.tonkeeper.api.collectibles.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonkeeper.api.fromJSON
import io.tonapi.models.NftItem

@Dao
interface CollectiblesDao {

    @Query("DELETE FROM collectibles WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(events: List<CollectiblesEntity>)

    suspend fun insert(accountId: String, events: List<NftItem>) {
        insert(CollectiblesEntity.map(accountId, events))
    }

    @Query("SELECT * FROM collectibles WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: String): List<CollectiblesEntity>

    @Query("SELECT * FROM collectibles WHERE nftAddress = :nftAddress LIMIT 1")
    suspend fun getItem(nftAddress: String): CollectiblesEntity?

    suspend fun get(accountId: String): List<NftItem> {
        return getByAccountId(accountId).map {
            fromJSON(it.data)
        }
    }
}