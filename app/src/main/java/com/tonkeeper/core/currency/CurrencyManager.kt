package com.tonkeeper.core.currency

import android.util.Log
import com.tonkeeper.App
import com.tonkeeper.core.widget.WidgetBalanceProvider
import com.tonkeeper.api.address
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.rates.RatesRepository
import com.tonkeeper.core.widget.Widget
import com.tonkeeper.core.widget.WidgetRateProvider
import com.tonkeeper.event.UpdateCurrencyRateEvent
import core.EventBus
import io.tonapi.models.TokenRates
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

        sync(accountId)

        EventBus.post(UpdateCurrencyRateEvent)

        Widget.updateAll()
    }

    suspend fun sync(accountId: String) {
        val jettons = jettonRepository.get(accountId)?.data?.map {
            it.address
        } ?: return

        repository.sync(accountId, jettons)
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
        return rates.diff24h?.get(currency) ?: ""
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
        return rates.diff7d?.get(currency) ?: ""
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
        return rates.prices?.get(currency)?.toFloat() ?: 0f
    }

    class Rates(
        private val map: Map<String, Any>
    ) {

        operator fun get(token: String): TokenRates? {
            return map[token] as? TokenRates
        }

        override fun toString(): String {
            return map.toString()
        }
    }


}