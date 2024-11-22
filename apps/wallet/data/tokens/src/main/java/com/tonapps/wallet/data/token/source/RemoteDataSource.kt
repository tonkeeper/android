package com.tonapps.wallet.data.token.source

import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class RemoteDataSource(
    private val api: API
) {

    fun getJetton(accountId: String, testnet: Boolean) = api.getJetton(accountId, testnet)

    suspend fun loadTON(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean
    ): BalanceEntity? = withContext(Dispatchers.IO) {
        api.getTonBalance(accountId, testnet, currency.code)
    }

    suspend fun loadJettons(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean
    ): List<BalanceEntity>? = withContext(Dispatchers.IO) {
        api.getJettonsBalances(
            accountId = accountId,
            testnet = testnet,
            currency = currency.code,
            extensions = listOf(
                TokenEntity.Extension.CustomPayload.value,
                TokenEntity.Extension.NonTransferable.value
            )
        )
    }

}