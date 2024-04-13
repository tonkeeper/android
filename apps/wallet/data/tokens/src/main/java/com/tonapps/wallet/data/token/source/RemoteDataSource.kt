package com.tonapps.wallet.data.token.source

import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.core.WalletCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class RemoteDataSource(
    private val api: API
) {

    suspend fun load(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean
    ): List<BalanceEntity> = withContext(Dispatchers.IO) {
        val tonBalanceDeferred = async {
            api.getTonBalance(accountId, testnet)
        }

        val jettonBalancesDeferred = async {
            api.getJettonsBalances(accountId, testnet, currency.code)
        }

        val account = tonBalanceDeferred.await()
        val jettons = jettonBalancesDeferred.await()
        listOf(account) + jettons
    }

}