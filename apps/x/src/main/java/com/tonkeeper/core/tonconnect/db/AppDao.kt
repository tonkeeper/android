package com.tonkeeper.core.tonconnect.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(appEntity: AppEntity)

    @Query("SELECT * FROM ton_connect_app WHERE accountId = :accountId AND clientId = :clientId LIMIT 1")
    suspend fun getAppEntity(accountId: String, clientId: String): AppEntity?

    @Query("SELECT * FROM ton_connect_app WHERE accountId = :accountId")
    suspend fun getApps(accountId: String): List<AppEntity>
}