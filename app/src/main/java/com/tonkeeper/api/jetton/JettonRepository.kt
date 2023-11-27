package com.tonkeeper.api.jetton

import android.util.Log
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.base.BaseAccountRepository
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.jetton.db.JettonDao
import com.tonkeeper.api.jetton.db.JettonEntity
import com.tonkeeper.api.symbol
import io.tonapi.apis.AccountsApi
import io.tonapi.models.JettonBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JettonRepository(
    private val api: AccountsApi = Tonapi.accounts,
    private val dao: JettonDao = App.db.jettonDao()
): BaseAccountRepository<JettonBalance>() {

    suspend fun getByAddress(
        accountId: String,
        address: String
    ): JettonBalance? {
        val data = dao.getByAddress(accountId, address) ?: return null
        return fromJSON(data)
    }

    override suspend fun insertCache(
        accountId: String,
        items: List<JettonBalance>
    ) {
        dao.insert(accountId, items)
    }

    override suspend fun fromCache(
        accountId: String
    ): List<JettonBalance> {
        return dao.get(accountId)
    }

    override fun find(
        value: String,
        items: List<JettonBalance>
    ): JettonBalance? {
        return items.find { it.symbol == value }
    }

    override fun fromCloud(
        accountId: String
    ): List<JettonBalance> {
        return api.getAccountJettonsBalances(
            accountId = accountId
        ).balances
    }

    override suspend fun clearCache(accountId: String) {
        dao.delete(accountId)
    }
}
