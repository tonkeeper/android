package com.tonapps.wallet.data.rates.entity

import android.os.Parcelable
import com.tonapps.log.L
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

@Parcelize
data class RatesEntity(
    val baseCurrency: WalletCurrency,
    private val map: Map<String, RateEntity>
): Parcelable {

    companion object {

        fun empty(currency: WalletCurrency): RatesEntity {
            return RatesEntity(currency, hashMapOf())
        }
    }

    val isEmpty: Boolean
        get() = map.isEmpty()

    val currencyCode: String
        get() = baseCurrency.code

    val tokens: List<String>
        get() = map.keys.toList()

    fun hasToken(token: String): Boolean {
        return map.containsKey(token)
    }

    fun hasTokens(tokens: List<String>): Boolean {
        for (token in tokens) {
            if (!hasToken(token)) {
                return false
            }
        }
        return true
    }

    fun merge(rates: List<RateEntity>): RatesEntity {
        val newMap = map.toMutableMap()
        for (rate in rates) {
            newMap[rate.tokenCode] = rate.copy()
        }
        return copy(map = newMap.toMap())
    }

    fun filter(tokens: List<String>): RatesEntity {
        val result = hashMapOf<String, RateEntity>()
        for (token in tokens) {
            val rate = map[token] ?: continue
            result[token] = rate
        }
        return RatesEntity(baseCurrency, result)
    }

    fun rate(token: String): RateEntity? {
        return map[token]
    }

    fun rateValue(token: String): Coins {
        return rate(token)?.value ?: Coins.ZERO
    }

    fun rateDiff(token: String): RateDiffEntity? {
        return rate(token)?.diff
    }

    fun convertTON(value: Coins): Coins {
        return convert(TokenEntity.TON.address, value)
    }

    private fun princeInBaseCurrency(currency: WalletCurrency): Coins? {
        if (baseCurrency == currency) {
            return Coins.ONE
        }
        return map[currency.tokenQuery]?.value
    }

    fun convert(
        from: WalletCurrency,
        value: Coins,
        to: WalletCurrency
    ): Coins {
        if (from == to) {
            return value
        }
        if (!value.isPositive || isEmpty) {
            return Coins.ZERO
        }
        val fromRate = princeInBaseCurrency(from)
        val toRate = princeInBaseCurrency(to)
        if (fromRate == null || toRate == null) {
            return Coins.ZERO
        }
        return value * (fromRate / toRate)
    }

    private fun convertJetton(
        from: WalletCurrency,
        value: Coins,
        to: WalletCurrency
    ): Coins {
        if (from == to || value.isZero) {
            return value
        }
        val fromRate = rateValue(from.address)
        val toRate = rateValue(to.address)
        if (fromRate.isZero || toRate.isZero) {
            return Coins.of(BigDecimal.ZERO, to.decimals)
        }
        val fiatValue = value.value.multiply(fromRate.value, Coins.mathContext)
        val finalAmount = fiatValue.divide(toRate.value, to.decimals, RoundingMode.HALF_EVEN)
        return Coins.of(finalAmount, to.decimals)
    }

    fun convert(token: String, value: Coins): Coins {
        if (baseCurrency.code == token || value == Coins.ZERO) {
            return value
        }

        val rate = rateValue(token)
        return (value * rate)
    }

    fun convertFromFiat(token: String, value: Coins): Coins {
        if (baseCurrency.code == token || value == Coins.ZERO) {
            return value
        }

        val rate = rateValue(token)
        return value.div(rate, roundingMode = RoundingMode.HALF_EVEN)
    }

    fun getRate(token: String): Coins {
        return rateValue(token)
    }

    fun getDiff24h(token: String): String {
        return rateDiff(token)?.diff24h ?: ""
    }

    fun getDiff7d(token: String): String {
        return rateDiff(token)?.diff7d ?: ""
    }

    fun getDiff30d(token: String): String {
        return rateDiff(token)?.diff30d ?: ""
    }
}