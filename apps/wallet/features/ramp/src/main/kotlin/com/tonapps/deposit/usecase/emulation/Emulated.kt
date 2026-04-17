package com.tonapps.deposit.usecase.emulation

import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.blockchain.model.legacy.Fee
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.core.entity.TransferType
import com.tonapps.wallet.data.rates.RatesRepository
import io.tonapi.models.MessageConsequences
import kotlin.math.abs

data class Emulated(
    val consequences: MessageConsequences?,
    val type: TransferType,
    val total: Total,
    val extra: Extra,
    val currency: WalletCurrency,
    val failed: Boolean = false,
    val error: Throwable? = null,
) {

    companion object {
        val defaultExtra = Extra(false, Coins.ONE, Coins.ONE)

        suspend fun Emulated.buildFee(
            wallet: WalletEntity,
            api: API,
            accountRepository: AccountRepository,
            batteryRepository: BatteryRepository,
            ratesRepository: RatesRepository
        ): SendFee {
            return if (withBattery && consequences != null) {
                val extra = consequences.event.extra
                val chargesBalance = 0 // TODO REQ BatteryHelper.getBatteryCharges(wallet, accountRepository, batteryRepository)
                val batteryConfig = batteryRepository.getConfig(wallet.network)
                val charges = BatteryMapper.calculateChargesAmount(
                    Coins.of(abs(extra)).value,
                    batteryConfig.chargeCost
                )
                val excessesAddress = batteryConfig.excessesAddress
                val rates = ratesRepository.getTONRates(wallet.network, currency)
                val converted = rates.convertTON(Coins.of(abs(extra)))
                SendFee.Battery(
                    charges = charges,
                    chargesBalance = chargesBalance,
                    extra = extra,
                    excessesAddress = excessesAddress!!,
                    fiatAmount = converted,
                    fiatCurrency = currency,
                )
            } else {
                val fee = Fee(extra.value, extra.isRefund)
                val rates = ratesRepository.getTONRates(wallet.network, currency)
                val converted = rates.convertTON(fee.value)
                SendFee.Ton(
                    amount = fee,
                    fiatAmount = converted,
                    fiatCurrency = currency,
                )
            }
        }
    }

    val nftCount: Int
        get() = total.nftCount

    val totalFormat: CharSequence
        get() = CurrencyFormatter.format(currency.code, total.totalFiat)

    val withBattery: Boolean
        get() = type == TransferType.Battery || type == TransferType.Gasless

    val totalFees: Coins
        get() = consequences?.let {
            Coins.of(it.trace.transaction.totalFees)
        } ?: Coins.ZERO

    data class Total(
        val totalFiat: Coins,
        val nftCount: Int,
        val isDangerous: Boolean,
    )

    data class Extra(
        val isRefund: Boolean,
        val value: Coins,
        val fiat: Coins,
    )
}