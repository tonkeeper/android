package com.tonapps.tonkeeper.core.currency

import com.tonapps.tonkeeper.api.getAddress
import com.tonapps.tonkeeper.api.jetton.JettonRepository
import com.tonapps.tonkeeper.api.rates.RatesRepository
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.event.UpdateCurrencyRateEvent
import core.EventBus
import io.tonapi.models.TokenRates
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
        val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return
        val accountId = wallet.accountId

        sync(accountId, wallet.testnet)

        EventBus.post(UpdateCurrencyRateEvent)

        Widget.updateAll()
    }

    suspend fun sync(accountId: String, testnet: Boolean) {
        val jettons = jettonRepository.get(accountId, testnet)?.data?.map {
            it.getAddress(testnet).toRawAddress()
        } ?: return

        repository.sync(accountId, testnet, jettons)
    }

    suspend fun get(
        accountId: String,
        testnet: Boolean
    ): Rates? {
        val rates = repository.get(accountId, testnet)?.rates ?: return null
        return Rates(rates)
    }

    suspend fun getRate24h(
        accountId: String,
        testnet: Boolean,
        token: String,
        currency: String
    ): String {
        val rates = get(accountId, testnet) ?: return EMPTY_DIFF_RATE
        val tokenRates = rates[token] ?: return EMPTY_DIFF_RATE
        return tokenRates.diff24h?.get(currency) ?: EMPTY_DIFF_RATE
    }

    suspend fun getRate7d(
        accountId: String,
        testnet: Boolean,
        token: String,
        currency: String
    ): String {
        val rates = get(accountId, testnet) ?: return EMPTY_DIFF_RATE
        val tokenRates = rates[token] ?: return EMPTY_DIFF_RATE
        return tokenRates.diff7d?.get(currency) ?: EMPTY_DIFF_RATE
    }

    suspend fun getRate(
        accountId: String,
        testnet: Boolean,
        token: String,
        currency: String
    ): Float {
        val rates = get(accountId, testnet) ?: return 0f
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