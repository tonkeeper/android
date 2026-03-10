package com.tonapps.wallet.data.battery.source

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.log.L
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class RemoteDataSource(
    private val api: API
) {

    suspend fun fetchBalance(
        tonProofToken: String,
        network: TonNetwork
    ): BatteryBalanceEntity? = withContext(Dispatchers.IO) {
        val response = api.getBatteryBalance(tonProofToken, network) ?: return@withContext null

        BatteryBalanceEntity(
            balance = Coins.of(response.balance.toBigDecimal(), 20),
            reservedBalance = Coins.of(response.reserved.toBigDecimal(), 20)
        )
    }

    suspend fun fetchConfig(
        network: TonNetwork
    ): BatteryConfigEntity? = withContext(Dispatchers.IO) {
        val configDeferred = async { api.getBatteryConfig(network) }
        val rechargeMethodsDeferred = async { api.getBatteryRechargeMethods(network) }

        val config = configDeferred.await() ?: return@withContext null
        val rechargeMethods = rechargeMethodsDeferred.await() ?: return@withContext null

        BatteryConfigEntity(
            excessesAccount = config.excessAccount,
            fundReceiver = config.fundReceiver,
            rechargeMethods = rechargeMethods.methods.map(::RechargeMethodEntity),
            gasProxy = config.gasProxy.map { it.address },
            meanPrices = BatteryConfigEntity.MeanPrices(
                batteryMeanPriceSwap = config.meanPrices.batteryMeanPriceSwap,
                batteryMeanPriceJetton = config.meanPrices.batteryMeanPriceJetton,
                batteryMeanPriceNft = config.meanPrices.batteryMeanPriceNft,
                batteryMeanPriceTronUsdt = config.meanPrices.batteryMeanPriceTronUsdt ?: 0,
                tonMeanPriceTronUsdt = config.meanPrices.tonMeanPriceTronUsdt ?: 0f
            ),
            chargeCost = config.chargeCost,
            reservedAmount = config.batteryReservedAmount
        )
    }

}