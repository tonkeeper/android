package com.tonkeeper.api.jetton

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.address
import com.tonkeeper.api.base.BaseAccountRepository
import com.tonkeeper.api.base.RepositoryResponse
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.jetton.db.JettonDao
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.api.symbol
import io.tonapi.apis.AccountsApi
import io.tonapi.models.JettonBalance

class JettonRepository(
    private val api: AccountsApi = Tonapi.accounts,
    private val dao: JettonDao = App.db.jettonDao()
): BaseAccountRepository<JettonBalance>() {

    private companion object {
        private fun List<JettonBalance>.prepare(): List<JettonBalance> {
            return this.filter {
                it.parsedBalance > 0f
            }.sortedBy {
                it.symbol
            }
        }
    }

    override suspend fun get(accountId: String): RepositoryResponse<List<JettonBalance>>? {
        val response = super.get(accountId) ?: return null

        return response.copy(
            data = response.data.prepare()
        )
    }

    override suspend fun deleteCache(accountId: String) {
        dao.delete(accountId)
    }

    suspend fun getByAddress(
        accountId: String,
        address: String
    ): JettonBalance? {
        var jetton: JettonBalance? = dao.getByAddress(accountId, address)?.let { fromJSON(it) }
        if (jetton == null) {
            jetton = getFromCloud(accountId)?.data?.find {
                it.address == address
            }
        }
        return jetton
    }

    override suspend fun insertCache(
        accountId: String,
        items: List<JettonBalance>
    ) {
        dao.insert(accountId, items)
    }

    override suspend fun onCacheRequest(
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

    override fun onFetchRequest(
        accountId: String
    ): List<JettonBalance> {
        val balances = api.getAccountJettonsBalances(
            accountId = accountId
        ).balances

        return balances.prepare()
    }
}
