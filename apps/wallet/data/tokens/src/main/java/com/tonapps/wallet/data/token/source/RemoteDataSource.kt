package com.tonapps.wallet.data.token.source

import com.tonapps.wallet.api.TonapiHelper
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.core.WalletCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class RemoteDataSource {

    suspend fun load(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean
    ): List<BalanceEntity> = withContext(Dispatchers.IO) {
        val tonBalanceDeferred = async {
            TonapiHelper.getTonBalance(accountId, testnet)
        }

        val jettonBalancesDeferred = async {
            TonapiHelper.getJettonsBalances(accountId, testnet, currency.code)
        }

        val account = tonBalanceDeferred.await()
        val jettons = jettonBalancesDeferred.await()
        listOf(account) + jettons
    }

}