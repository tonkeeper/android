package com.tonapps.tonkeeper.api.collectibles.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonapps.tonkeeper.api.fromJSON
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

    @Query("SELECT data FROM collectibles WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: String): List<String>

    @Query("SELECT data FROM collectibles WHERE nftAddress = :nftAddress LIMIT 1")
    suspend fun getItemData(nftAddress: String): String?

    suspend fun get(accountId: String): List<NftItem> {
        return getByAccountId(accountId).map {
            fromJSON(it)
        }
    }
}