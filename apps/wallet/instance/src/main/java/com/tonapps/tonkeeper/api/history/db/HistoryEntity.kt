package com.tonapps.tonkeeper.api.history.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tonapps.tonkeeper.api.toJSON
import io.tonapi.models.AccountEvent

@Entity(
    tableName = "event",
    indices = [
        Index(value = ["accountId"]),
    ]
)
data class HistoryEntity(
    @PrimaryKey val eventId: String,
    val accountId: String,
    val data: String,
    val timestamp: Long
) {

    companion object {

        fun map(accountId: String, list: List<AccountEvent>): List<HistoryEntity> {
            return list.map { HistoryEntity(
                accountId = accountId,
                event = it
            ) }
        }
    }

    constructor(
        accountId: String,
        event: AccountEvent
    ) : this(
        eventId = event.eventId,
        accountId = accountId,
        data = toJSON(event),
        timestamp = event.timestamp
    )
}

