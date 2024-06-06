package com.tonapps.wallet.data.token.source

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
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
        val entities = mutableListOf<BalanceEntity>()

        val tonBalanceDeferred = async {
            api.getTonBalance(accountId, testnet)
        }

        val jettonBalancesDeferred = async {
            api.getJettonsBalances(accountId, testnet, currency.code)
        }

        entities.add(tonBalanceDeferred.await())
        val jettons = jettonBalancesDeferred.await().toMutableList()
        val usdtIndex = jettons.indexOfFirst {
            it.token.address == TokenEntity.USDT.address
        }

        if (usdtIndex == -1) {
            entities.add(BalanceEntity(
                token = TokenEntity.USDT,
                value = Coins.ZERO,
                walletAddress = accountId
            ))
        } else {
            jettons[usdtIndex] = jettons[usdtIndex].copy(
                token = TokenEntity.USDT
            )
        }

        entities.addAll(jettons)
        entities.toList()
    }

}