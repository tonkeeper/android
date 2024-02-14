package com.tonapps.tonkeeper.api.jetton

import com.tonapps.tonkeeper.api.Tonapi
import com.tonapps.tonkeeper.api.base.AccountKey
import com.tonapps.tonkeeper.api.base.BaseAccountRepository
import com.tonapps.tonkeeper.api.base.RepositoryResponse
import com.tonapps.tonkeeper.api.base.SourceAPI
import com.tonapps.tonkeeper.api.fromJSON
import com.tonapps.tonkeeper.api.getAddress
import com.tonapps.tonkeeper.api.jetton.db.JettonDao
import com.tonapps.tonkeeper.api.parsedBalance
import com.tonapps.tonkeeper.api.symbol
import io.tonapi.apis.AccountsApi
import io.tonapi.models.JettonBalance

class JettonRepository(
    private val api: SourceAPI<AccountsApi> = Tonapi.accounts,
    private val dao: JettonDao = com.tonapps.tonkeeper.App.db.jettonDao()
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

    override suspend fun get(
        accountId: String,
        testnet: Boolean
    ): RepositoryResponse<List<JettonBalance>>? {
        val response = super.get(accountId, testnet) ?: return null

        return response.copy(
            data = response.data.prepare()
        )
    }

    override suspend fun deleteCache(accountKey: AccountKey) {
        dao.delete(accountKey.toString())
    }

    suspend fun getByAddress(
        accountId: String,
        address: String,
        testnet: Boolean,
    ): JettonBalance? {
        var jetton: JettonBalance? = dao.getByAddress(accountId, address)?.let { fromJSON(it) }
        if (jetton == null) {
            jetton = getFromCloud(accountId, testnet)?.data?.find {
                it.getAddress(testnet) == address
            }
        }
        return jetton
    }

    override suspend fun insertCache(
        accountKey: AccountKey,
        items: List<JettonBalance>
    ) {
        dao.insert(accountKey.toString(), accountKey.testnet, items)
    }

    override suspend fun onCacheRequest(
        accountKey: AccountKey
    ): List<JettonBalance> {
        return dao.get(accountKey.toString())
    }

    override fun find(
        value: String,
        items: List<JettonBalance>
    ): JettonBalance? {
        return items.find { it.symbol == value }
    }

    override fun onFetchRequest(
        accountId: String,
        testnet: Boolean,
    ): List<JettonBalance> {
        val balances = api.get(testnet).getAccountJettonsBalances(
            accountId = accountId
        ).balances

        return balances.prepare()
    }
}
