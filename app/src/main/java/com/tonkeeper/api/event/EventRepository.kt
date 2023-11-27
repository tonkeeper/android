package com.tonkeeper.api.event

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.base.BaseAccountRepository
import com.tonkeeper.api.event.db.EventDao
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.withRetry
import io.tonapi.apis.AccountsApi
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EventRepository(
    private val api: AccountsApi = Tonapi.accounts,
    private val dao: EventDao = App.db.eventDao()
): BaseAccountRepository<AccountEvent>() {

    override suspend fun fromCache(
        accountId: String
    ): List<AccountEvent> {
        return dao.get(accountId)
    }

    suspend fun getByEventId(
        accountId: String,
        eventId: String
    ): AccountEvent? {
        val data = dao.getByEventId(accountId, eventId)
        return if (data != null) {
            fromJSON(data)
        } else {
            getSingle(accountId, eventId)
        }
    }

    override fun find(
        value: String,
        items: List<AccountEvent>
    ): AccountEvent? {
        return items.find {
            it.eventId == value
        }
    }

    override fun fromCloud(
        accountId: String
    ): List<AccountEvent> {
        return api.getAccountEvents(
            accountId = accountId,
            limit = 100
        ).events
    }

    override suspend fun clearCache(accountId: String) {
        dao.delete(accountId)
    }

    override suspend fun insertCache(accountId: String, items: List<AccountEvent>) {
        dao.insert(accountId, items)
    }
}
