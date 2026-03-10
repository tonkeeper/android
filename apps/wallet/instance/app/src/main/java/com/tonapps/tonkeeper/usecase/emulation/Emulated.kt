package com.tonapps.tonkeeper.usecase.emulation

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.Fee
import com.tonapps.tonkeeper.helper.BatteryHelper
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendFee
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.entity.TransferType
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.token.TokenRepository
import io.tonapi.models.JettonQuantity
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
                val chargesBalance = BatteryHelper.getBatteryCharges(wallet, accountRepository, batteryRepository)
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

    val totalTon: Coins
        get() = consequences?.let {
            Coins.of(it.risk.ton)
        } ?: Coins.ZERO

    val totalFees: Coins
        get() = consequences?.let {
            Coins.of(it.trace.transaction.totalFees)
        } ?: Coins.ZERO

    val jettons: List<JettonQuantity>
        get() = consequences?.risk?.jettons ?: emptyList()

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

    suspend fun loadTokens(network: TonNetwork, tokenRepository: TokenRepository): List<TokenEntity> {
        val jettonsAddress = jettons.map {
            it.jetton.address.toRawAddress()
        }

        return tokenRepository.getTokens(network, jettonsAddress)
    }

}