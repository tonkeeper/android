package com.tonkeeper.core.currency

import android.util.Log
import com.tonkeeper.App
import com.tonkeeper.api.address
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.rates.RatesRepository
import com.tonkeeper.core.widget.Widget
import com.tonkeeper.event.UpdateCurrencyRateEvent
import core.EventBus
import io.tonapi.models.TokenRates
import org.ton.block.AddrStd
import ton.SupportedCurrency
import ton.SupportedTokens
import ton.extensions.toRawAddress

class CurrencyManager {

    companion object {

        const val EMPTY_DIFF_RATE = "-"

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
            it.address.toRawAddress()
        } ?: return

        repository.sync(accountId, jettons)
    }

    suspend fun get(accountId: String): Rates? {
        val rates = repository.get(accountId)?.rates ?: return null
        return Rates(rates)
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
        val rates = get(accountId) ?: return EMPTY_DIFF_RATE
        val tokenRates = rates[token] ?: return EMPTY_DIFF_RATE
        return tokenRates.diff24h?.get(currency) ?: EMPTY_DIFF_RATE
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
        val rates = get(accountId) ?: return EMPTY_DIFF_RATE
        val tokenRates = rates[token] ?: return EMPTY_DIFF_RATE
        return tokenRates.diff7d?.get(currency) ?: EMPTY_DIFF_RATE
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
        val rates = get(accountId) ?: return 0f
        val tokenRates = rates[token] ?: return 0f
        val prices = tokenRates.prices ?: return 0f
        return prices[currency]?.toFloat() ?: 0f
    }

    class Rates(
        private val map: Map<String, Any>
    ) {

        operator fun get(token: String): TokenRates? {
            val rates = map[token] as? TokenRates
            if (rates != null) {
                return rates
            }
            val fixedToken = token.toRawAddress()
            return map[fixedToken] as? TokenRates
        }

        override fun toString(): String {
            return map.toString()
        }
    }


}