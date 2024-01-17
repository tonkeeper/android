package com.tonkeeper.api.history

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.base.AccountKey
import com.tonkeeper.api.base.BaseAccountRepository
import com.tonkeeper.api.base.SourceAPI
import com.tonkeeper.api.history.db.HistoryDao
import com.tonkeeper.api.withRetry
import com.tonkeeper.core.history.HistoryHelper
import io.tonapi.apis.AccountsApi
import io.tonapi.models.AccountEvent

class HistoryRepository(
    private val api: SourceAPI<AccountsApi> = Tonapi.accounts,
    private val dao: HistoryDao = App.db.historyDao()
): BaseAccountRepository<AccountEvent>() {

    override suspend fun deleteCache(accountKey: AccountKey) {
        dao.delete(accountKey.toString())
    }

    override suspend fun onCacheRequest(
        accountKey: AccountKey
    ): List<AccountEvent> {
        return dao.get(accountKey.toString())
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
        accountId: String,
        testnet: Boolean,
    ): List<AccountEvent> {
        return api.get(testnet).getAccountEvents(
            accountId = accountId,
            limit = HistoryHelper.EVENT_LIMIT,
        ).events
    }

    suspend fun getWithOffset(
        accountId: String,
        testnet: Boolean,
        beforeLt: Long
    ): List<AccountEvent>? {
        return withRetry {
            api.get(testnet).getAccountEvents(
                accountId = accountId,
                limit = HistoryHelper.EVENT_LIMIT,
                beforeLt = beforeLt
            ).events
        }
    }

    override suspend fun insertCache(accountKey: AccountKey, items: List<AccountEvent>) {
        val key = accountKey.toString()
        dao.delete(key)
        dao.insert(key, items)
    }
}
