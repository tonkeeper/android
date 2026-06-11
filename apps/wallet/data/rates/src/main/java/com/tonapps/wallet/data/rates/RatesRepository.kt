package com.tonapps.wallet.data.rates

import android.content.Context
import com.tonapps.icu.Coins
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.rates.entity.RateDiffEntity
import com.tonapps.wallet.data.rates.entity.RateEntity
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.data.rates.source.BlobDataSource
import io.tonapi.models.TokenRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class RatesRepository(
    context: Context,
    private val api: API
) {

    private val localDataSource = BlobDataSource(context)
    private val manager = RateManager()

    suspend fun getRate(
        network: TonNetwork,
        from: WalletCurrency,
        to: WalletCurrency,
        baseCurrency: WalletCurrency = WalletCurrency.USD
    ): RateData? = withContext(Dispatchers.IO) {
        if (!manager.hasRate(from, to)) {
            val all = listOf(from, to, baseCurrency)
            val currency = all.first { it.fiat }
            val tokens = all.filter { !it.fiat }
            val response = fetchRates(network, currency.code, tokens.map { it.tokenQuery })
            for (token in tokens) {
                val tokenRates = response[token.tokenQuery] ?: continue
                val price = tokenRates.prices?.get(currency.code)?.let {
                    Coins.of(it, currency.decimals)
                } ?: continue
                manager.addRate(token, currency, price)
            }
        }
        manager.getRate(from, to)?.let {
            RateData(from, to, it)
        }
    }

    suspend fun convert(
        network: TonNetwork,
        amount: Coins,
        from: WalletCurrency,
        to: WalletCurrency
    ): Coins {
        getRate(network, from, to)
        return manager.convert(amount, from, to) ?: Coins.ZERO
    }

    suspend fun updateAll(network: TonNetwork, currency: WalletCurrency, tokens: List<String>) = withContext(Dispatchers.IO) {
        load(network, currency, tokens.take(100).toMutableList())
    }

    suspend fun updateAll(network: TonNetwork, currency: WalletCurrency) = withContext(Dispatchers.IO) {
        updateAll(network, currency, localDataSource.get(network, currency).tokens)
    }

    fun cache(network: TonNetwork, currency: WalletCurrency, tokens: List<String>): RatesEntity {
        return localDataSource.get(network, currency).filter(tokens)
    }

    fun load(network: TonNetwork, currency: WalletCurrency, token: String) {
        load(network, currency, mutableListOf(token))
    }

    fun load(network: TonNetwork, currency: WalletCurrency, tokens: MutableList<String>) {
        if (!tokens.contains(TokenEntity.TON.address)) {
            tokens.add(TokenEntity.TON.address)
        }
        if (!tokens.contains(TokenEntity.USDT.address)) {
            tokens.add(TokenEntity.USDT.address)
        }
        val rates = mutableMapOf<String, TokenRates>()
        for (chunk in tokens.chunked(100)) {
            runCatching { fetchRates(network, currency.code, chunk) }.onSuccess(rates::putAll)
        }
        val usdtRate = rates[TokenEntity.USDT.address]
        usdtRate?.let {
            rates.put(TokenEntity.TRON_USDT.address, usdtRate)
        }
        insertRates(network, currency, rates)
    }

    private fun fetchRates(network: TonNetwork, code: String, tokens: List<String>): Map<String, TokenRates> {
        if (tokens.size > 100) {
            throw IllegalArgumentException("Too many tokens requested: ${tokens.size}")
        }
        return api.getRates(network, code, tokens) ?: throw IllegalStateException("Failed to fetch rates for $code with tokens: $tokens")
    }

    fun insertRates(network: TonNetwork, currency: WalletCurrency, rates: Map<String, TokenRates>) {
        if (rates.isEmpty()) {
            return
        }
        val entities = mutableListOf<RateEntity>()
        for (rate in rates) {
            val value = rate.value
            val prices = value.prices?.get(currency.code)?.let(::BigDecimal)
            val bigDecimal = prices ?: BigDecimal.ZERO

            entities.add(RateEntity(
                tokenCode = rate.key,
                currency = currency,
                value = Coins.of(bigDecimal, currency.decimals),
                diff = RateDiffEntity(currency, value),
            ))
        }
        localDataSource.add(network, currency, entities)
    }

    private fun getCachedRates(network: TonNetwork, currency: WalletCurrency, tokens: List<String>): RatesEntity {
        return localDataSource.get(network, currency).filter(tokens)
    }

    suspend fun getRates(network: TonNetwork, currency: WalletCurrency, token: String): RatesEntity {
        return getRates(network, currency, listOf(token))
    }

    suspend fun getTONRates(network: TonNetwork, currency: WalletCurrency): RatesEntity {
        return getRates(network, currency, TokenEntity.TON.address)
    }

    suspend fun getRates(
        network: TonNetwork,
        currency: WalletCurrency,
        tokens: List<String>
    ): RatesEntity = withContext(Dispatchers.IO) {
        val rates = getCachedRates(network, currency, tokens)
        if (rates.hasTokens(tokens)) {
            rates
        } else {
            load(network, currency, tokens.toMutableList())
            getCachedRates(network, currency, tokens)
        }
    }
}
