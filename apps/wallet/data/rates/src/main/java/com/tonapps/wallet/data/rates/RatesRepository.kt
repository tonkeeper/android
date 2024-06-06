package com.tonapps.wallet.data.rates

import android.content.Context
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.entity.RateDiffEntity
import com.tonapps.wallet.data.rates.entity.RateEntity
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.data.rates.source.BlobDataSource
import io.tonapi.models.TokenRates
import java.math.BigDecimal

class RatesRepository(
    context: Context,
    private val api: API
) {

    private val localDataSource = BlobDataSource(context)

    fun cache(currency: WalletCurrency, tokens: List<String>): RatesEntity {
        return localDataSource.get(currency).filter(tokens)
    }

    fun load(currency: WalletCurrency, token: String) {
        load(currency, mutableListOf(token))
    }

    fun load(currency: WalletCurrency, tokens: MutableList<String>) {
        if (!tokens.contains("TON")) {
            tokens.add("TON")
        }
        val rates = api.getRates(currency.code, tokens)
        insertRates(currency, rates)
    }

    fun insertRates(currency: WalletCurrency, rates: Map<String, TokenRates>) {
        if (rates.isEmpty()) {
            return
        }
        val entities = mutableListOf<RateEntity>()
        for (rate in rates) {
            val value = rate.value
            val bigDecimal = value.prices?.get(currency.code) ?: BigDecimal.ZERO

            entities.add(RateEntity(
                tokenCode = rate.key,
                currency = currency,
                value = Coins.of(bigDecimal, currency.decimals),
                diff = RateDiffEntity(currency, value),
            ))
        }
        localDataSource.add(currency, entities)
    }

    fun getRates(currency: WalletCurrency, token: String): RatesEntity {
        if (token == TokenEntity.USDT.address) {
            return RatesEntity(currency, hashMapOf<String, RateEntity>().apply {
                put(token, RateEntity(
                    tokenCode = token,
                    currency = currency,
                    value = Coins.ONE,
                    diff = RateDiffEntity("", "", ""),
                ))
            })
        }
        return getRates(currency, listOf(token))
    }

    fun getRates(currency: WalletCurrency, tokens: List<String>): RatesEntity {
        return localDataSource.get(currency).filter(tokens)
    }
}