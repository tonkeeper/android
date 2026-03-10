package com.tonapps.wallet.data.rates

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.core.currency.WalletCurrency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

internal class RateManager {

    private val rates = ConcurrentHashMap<String, Coins>()

    fun addRate(from: WalletCurrency, to: WalletCurrency, price: Coins) {
        if (!price.isPositive) {
            return
        }

        price.value.stripTrailingZeros()

        rates[getCacheKey(from, to)] = price

        val inverseRate = BigDecimal.ONE.divide(
            price.value,
            maxOf(from.decimals, to.decimals, 18),
            RoundingMode.HALF_UP
        )

        rates[getCacheKey(to, from)] = Coins(inverseRate, to.decimals)
    }

    fun getRate(from: WalletCurrency, to: WalletCurrency): Coins? {
        if (from == to) {
            return Coins.ONE.withNewDecimals(from.decimals)
        }
        return rates[getCacheKey(from, to)]
    }

    fun hasRate(from: WalletCurrency, to: WalletCurrency): Boolean {
        return rates.containsKey(getCacheKey(from, to))
    }

    fun convert(amount: Coins, from: WalletCurrency, to: WalletCurrency): Coins? {
        val rate = getRate(from, to) ?: return null

        val divisor = BigDecimal.TEN.pow(from.decimals)
        val scale = from.decimals + 6
        val resultInBaseUnits = amount.value
            .divide(divisor, scale, RoundingMode.HALF_UP)
            .multiply(rate.value, MathContext.DECIMAL128)

        val multiplicand = BigDecimal.TEN.pow(to.decimals)
        val resultInMinimalUnits = resultInBaseUnits
            .multiply(multiplicand, MathContext.DECIMAL128)
            .setScale(0, RoundingMode.DOWN)

        return Coins(resultInMinimalUnits, to.decimals)
    }

    private fun getCacheKey(from: WalletCurrency, to: WalletCurrency): String {
        return "${from.key}->${to.key}"
    }
}