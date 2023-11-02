package com.tonkeeper.tonconnect.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ManifestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manifestEntity: ManifestEntity)

    suspend fun insert(url: String, data: String) {
        insert(ManifestEntity(url, data))
    }

    @Query("SELECT * FROM ton_connect_manifest WHERE url = :url LIMIT 1")
    suspend fun get(url: String): ManifestEntity?

}