package com.tonapps.tonkeeper.ui.screen.onramp.main.state

import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.purchase.entity.OnRamp
import com.tonapps.wallet.data.purchase.entity.OnRamp.AllowedPair
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.data.rates.entity.RatesEntity

data class OnRampCurrencyInputs(
    val country: String = "UAE",
    val sell: CurrencyInputState = CurrencyInputState.Fiat(WalletCurrency.USD),
    val buy: CurrencyInputState = CurrencyInputState.Fiat(WalletCurrency.TON),
    val providers: List<PurchaseMethodEntity> = emptyList(),
    val rates: RatesEntity = RatesEntity.empty(WalletCurrency.TON),
    val switch: Boolean = false,
    val selectedProviderId: String? = null,
    val allowedPair: AllowedPair? = null,
    val merchants: List<OnRamp.Merchant> = emptyList(),
    val paymentType: String? = null,
    val availableFiatSlugs: List<String> = emptyList()
) {

    companion object {

        private val supportedMethods = listOf("card", "google_pay", "revolut_pay", "paypal")

        fun fixSymbol(value: String): String {
            if (value.equals("USD₮", ignoreCase = true)) {
                return "USDT"
            }
            return value
        }

        private fun fixMethods(values: List<String>): List<String> {
            return values.filter { supportedMethods.contains(it)  }
        }
    }

    val sellDecimals: Int
        get() = sell.decimals

    val buyDecimals: Int
        get() = buy.decimals

    val from: String
        get() = fixSymbol(sell.symbol)

    val to: String
        get() = fixSymbol(buy.symbol)

    val isTON: Boolean
        get() = sell.isTON || buy.isTON

    val isTron: Boolean
        get() = sell.isTron || buy.isTron

    val fromForAnalytics: String
        get() = if (sell.fiat) {
            "fiat"
        } else {
            "crypto_$from"
        }

    val toForAnalytics: String
        get() = if (buy.fiat) {
            "fiat"
        } else {
            "crypto_$to"
        }

    val selectedProvider: PurchaseMethodEntity? by lazy {
        providers.find { it.id.equals(selectedProviderId, ignoreCase = true) }
    }

    val hasProvider: Boolean by lazy {
        selectedProvider != null && providers.isNotEmpty()
    }

    val inputMerchantMethods: List<String> by lazy {
        if (!sell.fiat) {
            return@lazy emptyList()
        }
        val providerId = selectedProviderId ?: return@lazy emptyList<String>()
        val merchant = merchants.firstOrNull {
            it.slug.equals(providerId, ignoreCase = true)
        } ?: return@lazy emptyList<String>()
        fixMethods(merchant.inputMethods)
    }

    val outputMerchantMethods: List<String> by lazy {
        val providerId = selectedProviderId ?: return@lazy emptyList<String>()
        val merchant = merchants.firstOrNull {
            it.slug.equals(providerId, ignoreCase = true)
        } ?: return@lazy emptyList<String>()
        fixMethods(merchant.outputMethods)
    }

    val network: String
        get() {
            val network = sell.network
            if (network.equals("fiat", ignoreCase = true)) {
                return buy.network
            }
            return network
        }

    val type: String
        get() {
            if (sell.fiat) {
                return buy.type
            }
            return sell.type
        }

    val purchaseType: String
        get() = if (sell.fiat) "buy" else "sell"

    val rateFormat: CharSequence? by lazy {
        if (allowedPair == null || (sell.fiat && buy.fiat)) {
            return@lazy null
        }
        if ((sell.isUSD && buy.isUSDT) || (sell.isUSDT && buy.isUSD)) {
            val from = CurrencyFormatter.format("USD", Coins.ONE)
            val to = CurrencyFormatter.format("USDT", Coins.ONE)
            "$from ≈ $to"
        } else {
            val token = CurrencyInputState.findToken(sell, buy) ?: return@lazy null

            val from = if (sell.fiat) CurrencyFormatter.formatFiat(rates.currencyCode, Coins.ONE) else CurrencyFormatter.format(token.symbol, Coins.ONE)
            val price = if (sell.fiat) rates.convertFromFiat(token.address, Coins.ONE) else rates.convert(token.address, Coins.ONE)
            val to = if (sell.fiat) CurrencyFormatter.format(token.symbol, price) else CurrencyFormatter.formatFiat(rates.currency.code, price)
            "$from ≈ $to"
        }
    }

    fun rateConvert(amount: Coins, reverse: Boolean): Coins {
        return if (reverse) {
            if (sell.fiat) {
                rates.convert(buy.address, amount).setScale(buy.decimals)
            } else {
                rates.convertFromFiat(sell.address, amount).setScale(sell.decimals)
            }
        } else {
            if (sell.fiat) {
                rates.convertFromFiat(buy.address, amount).setScale(buy.decimals)
            } else {
                rates.convert(sell.address, amount).setScale(sell.decimals)
            }
        }
    }
}