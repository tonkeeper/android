package com.tonkeeper.core.tonconnect.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(appEntity: AppEntity)

    @Query("SELECT * FROM ton_connect_app WHERE accountId = :accountId AND url = :url")
    suspend fun getAppEntity(accountId: String, url: String): AppEntity

    @Query("SELECT clientId FROM ton_connect_app WHERE accountId = :accountId")
    suspend fun getClientIds(accountId: String): List<String>
}