package com.tonkeeper.api.event.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tonkeeper.api.toJSON
import io.tonapi.models.AccountEvent

@Entity(
    tableName = "event",
    indices = [
        Index(value = ["accountId"]),
    ]
)
data class EventEntity(
    @PrimaryKey val eventId: String,
    val accountId: String,
    val data: String
) {

    companion object {

        fun map(accountId: String, list: List<AccountEvent>): List<EventEntity> {
            return list.map { EventEntity(
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
        data = toJSON(event)
    )
}

