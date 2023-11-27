package com.tonkeeper.core.currency

import com.tonkeeper.App
import com.tonkeeper.core.widget.WidgetBalanceProvider
import com.tonkeeper.api.address
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.rates.RatesRepository
import com.tonkeeper.core.widget.Widget
import com.tonkeeper.core.widget.WidgetRateProvider
import com.tonkeeper.event.UpdateCurrencyRateEvent
import core.EventBus
import ton.SupportedCurrency
import ton.SupportedTokens

class CurrencyManager {

    companion object {

        @Volatile
        private var INSTANCE: CurrencyManager? = null

        fun getInstance(): CurrencyManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CurrencyManager()
                INSTANCE = instance
                instance
            }
        }
    }

    private val jettonRepository = JettonRepository()
    private val repository = RatesRepository()

    suspend fun sync() {
        val wallet = App.walletManager.getWalletInfo() ?: return
        val accountId = wallet.accountId

        val jettons = jettonRepository.get(accountId).map {
            it.address
        }

        repository.sync(accountId, jettons)

        EventBus.post(UpdateCurrencyRateEvent)

        Widget.updateAll()
    }

    suspend fun get(accountId: String): Rates {
        return Rates(repository.get(accountId).rates)
    }

    suspend fun getRate24h(
        accountId: String,
        token: SupportedTokens,
        currency: SupportedCurrency
    ): String {
        return getRate24h(accountId, token.name, currency.name)
    }

    suspend fun getRate24h(
        accountId: String,
        token: String,
        currency: String
    ): String {
        val rates = get(accountId)[token] ?: return ""
        return rates.diff24h[currency] ?: ""
    }

    suspend fun getRate7d(
        accountId: String,
        token: SupportedTokens,
        currency: SupportedCurrency
    ): String {
        return getRate7d(accountId, token.name, currency.name)
    }

    suspend fun getRate7d(
        accountId: String,
        token: String,
        currency: String
    ): String {
        val rates = get(accountId)[token] ?: return ""
        return rates.diff7d[currency] ?: ""
    }

    suspend fun getRate(
        accountId: String,
        token: SupportedTokens,
        currency: SupportedCurrency
    ): Float {
        return getRate(accountId, token.name, currency.name)
    }

    suspend fun getRate(
        accountId: String,
        token: String,
        currency: String
    ): Float {
        val rates = get(accountId)[token] ?: return 0f
        return rates.prices[currency]?.toFloat() ?: 0f
    }

    class Rates(
        private val map: Map<String, Any>
    ) {

        operator fun get(token: String): Token? {
            val value = map[token] as? Map<String, Any> ?: return null
            return Token(value)
        }
    }

    class Token(
        private val map: Map<String, Any>
    ) {

        val prices: Map<String, Double> by lazy {
            map["prices"] as? Map<String, Double> ?: emptyMap()
        }

        val diff24h: Map<String, String> by lazy {
            map["diff_24h"] as? Map<String, String> ?: emptyMap()
        }

        val diff7d: Map<String, String> by lazy {
            map["diff_7d"] as? Map<String, String> ?: emptyMap()
        }

        val diff30d: Map<String, String> by lazy {
            map["diff_30d"] as? Map<String, String> ?: emptyMap()
        }

        fun to(toCurrency: String, value: Float): Float {
            val price = prices[toCurrency] ?: return 0f
            return price.toFloat() * value
        }

    }


}