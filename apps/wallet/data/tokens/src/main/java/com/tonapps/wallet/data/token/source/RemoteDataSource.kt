package com.tonapps.wallet.data.token.source

import com.tonapps.blockchain.ton.TonNetwork
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class RemoteDataSource(
    private val api: API
) {

    fun getJetton(accountId: String, network: TonNetwork) = api.getJetton(accountId, network)

    suspend fun loadTON(
        currency: WalletCurrency,
        accountId: String,
        network: TonNetwork
    ): BalanceEntity? = withContext(Dispatchers.IO) {
        api.getTonBalance(accountId, network, currency.code)
    }

    suspend fun loadJettons(
        currency: WalletCurrency,
        accountId: String,
        network: TonNetwork
    ): List<BalanceEntity>? = withContext(Dispatchers.IO) {
        try {
            api.getJettonsBalances(
                accountId = accountId,
                network = network,
                currency = currency.code,
                extensions = listOf(
                    TokenEntity.Extension.CustomPayload.value,
                    TokenEntity.Extension.NonTransferable.value
                )
            )
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    suspend fun loadTronUsdt(
        tronAddress: String,
    ): BalanceEntity = withContext(Dispatchers.IO) {
        api.tron.getTronUsdtBalance(tronAddress)
    }

    suspend fun loadTronTrx(
        tronAddress: String,
    ): BalanceEntity = withContext(Dispatchers.IO) {
        api.tron.getTrxBalance(tronAddress)
    }

}