package com.tonapps.tonkeeper.api.nft.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.tonapi.models.NftItem

@Dao
interface NftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(nft: NftEntity)

    suspend fun insert(nftItem: NftItem) {
        insert(NftEntity(nftItem))
    }

    @Query("SELECT data FROM nft WHERE nftAddress = :nftAddress LIMIT 1")
    suspend fun get(nftAddress: String): String?

}