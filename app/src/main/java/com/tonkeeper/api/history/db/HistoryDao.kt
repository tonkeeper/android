package com.tonkeeper.api.history.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonkeeper.api.fromJSON
import io.tonapi.models.AccountEvent

@Dao
interface HistoryDao {

    @Query("DELETE FROM event WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(events: List<HistoryEntity>)

    suspend fun insert(accountId: String, events: List<AccountEvent>) {
        insert(HistoryEntity.map(accountId, events))
    }

    @Query("SELECT data FROM event WHERE accountId = :accountId ORDER BY timestamp DESC")
    suspend fun getByAccountId(accountId: String): List<String>

    @Query("SELECT data FROM event WHERE accountId = :accountId AND eventId = :eventId LIMIT 1")
    suspend fun getByEventId(accountId: String, eventId: String): String?

    suspend fun get(accountId: String): List<AccountEvent> {
        return getByAccountId(accountId).map {
            fromJSON(it)
        }
    }
}