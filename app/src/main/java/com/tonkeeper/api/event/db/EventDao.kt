package com.tonkeeper.api.event.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.jetton.db.JettonEntity
import io.tonapi.models.AccountEvent
import io.tonapi.models.JettonBalance

@Dao
interface EventDao {

    @Query("DELETE FROM event WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(events: List<EventEntity>)

    suspend fun insert(accountId: String, events: List<AccountEvent>) {
        insert(EventEntity.map(accountId, events))
    }

    @Query("SELECT * FROM event WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: String): List<EventEntity>

    @Query("SELECT * FROM event WHERE accountId = :accountId AND eventId = :eventId")
    suspend fun getByEventId(accountId: String, eventId: String): EventEntity?

    suspend fun get(accountId: String): List<AccountEvent> {
        return getByAccountId(accountId).map {
            fromJSON(it.data)
        }
    }
}