package com.tonkeeper.api.history

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.base.BaseAccountRepository
import com.tonkeeper.api.history.db.HistoryDao
import io.tonapi.apis.AccountsApi
import io.tonapi.models.AccountEvent

class HistoryRepository(
    private val api: AccountsApi = Tonapi.accounts,
    private val dao: HistoryDao = App.db.historyDao()
): BaseAccountRepository<AccountEvent>() {

    override suspend fun deleteCache(accountId: String) {
        dao.delete(accountId)
    }

    override suspend fun onCacheRequest(
        accountId: String
    ): List<AccountEvent> {
        return dao.get(accountId)
    }

    override fun find(
        value: String,
        items: List<AccountEvent>
    ): AccountEvent? {
        return items.find {
            it.eventId == value
        }
    }

    override fun onFetchRequest(
        accountId: String
    ): List<AccountEvent> {
        return api.getAccountEvents(
            accountId = accountId,
            limit = 30,
        ).events
    }

    override suspend fun insertCache(accountId: String, items: List<AccountEvent>) {
        dao.delete(accountId)
        dao.insert(accountId, items)
    }
}
