package com.tonapps.tonkeeper.ui.screen.battery.recharge.entity

import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import java.math.BigDecimal

data class RechargePackEntity(
    val type: String,
    val charges: Int,
    private val maxCharges: Int,
    private val rechargeMethod: RechargeMethodEntity,
    private val fiatRate: Coins,
    private val token: AccountTokenEntity,
    private val config: ConfigEntity,
    private val willBePaidManually: Boolean,
    private val currency: WalletCurrency,
    private val batteryConfig: BatteryConfigEntity,
) {
    private val amountInToken: BigDecimal
        get() = rechargeMethod.amountForCharges(charges, batteryConfig.chargeCost)

    val formattedAmount: CharSequence
        get() = CurrencyFormatter.formatFiat(
            currency = rechargeMethod.symbol,
            value = amountInToken,
        )

    val formattedFiatAmount: CharSequence
        get() = CurrencyFormatter.formatFiat(
            currency = currency.code,
            value = amountInToken.multiply(fiatRate.value)
        )

    val batteryLevel: Float
        get() = charges.toFloat() / maxCharges

    val transactions: Map<BatteryTransaction, Int>
        get() = mapOf(
            BatteryTransaction.SWAP to charges / batteryConfig.meanPrices.batteryMeanPriceSwap,
            BatteryTransaction.NFT to charges / batteryConfig.meanPrices.batteryMeanPriceNft,
            BatteryTransaction.JETTON to charges / batteryConfig.meanPrices.batteryMeanPriceJetton
        )

    val isAvailableToBuy: Boolean
        get() = willBePaidManually || rechargeMethod.minBootstrapValue?.let { amountInToken >= it.toBigDecimal() } ?: false

    private val isEnoughBalance: Boolean
        get() = token.balance.value.value >= amountInToken

    val isEnabled: Boolean
        get() = isAvailableToBuy && isEnoughBalance
}
