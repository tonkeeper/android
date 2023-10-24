package com.tonkeeper.api.nft.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonkeeper.api.fromJSON
import io.tonapi.models.NftItem

@Dao
interface NftDao {

    @Query("DELETE FROM nft WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(events: List<NftEntity>)

    suspend fun insert(accountId: String, events: List<NftItem>) {
        insert(NftEntity.map(accountId, events))
    }

    @Query("SELECT * FROM nft WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: String): List<NftEntity>

    suspend fun get(accountId: String): List<NftItem> {
        return getByAccountId(accountId).map {
            fromJSON(it.data)
        }
    }
}