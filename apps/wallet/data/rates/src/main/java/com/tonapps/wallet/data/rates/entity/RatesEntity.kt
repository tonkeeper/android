package com.tonapps.wallet.data.rates.entity

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import kotlinx.parcelize.Parcelize
import java.math.RoundingMode

@Parcelize
data class RatesEntity(
    val currency: WalletCurrency,
    private val map: Map<String, RateEntity>
): Parcelable {

    companion object {

        fun empty(currency: WalletCurrency): RatesEntity {
            return RatesEntity(currency, hashMapOf())
        }
    }

    val isEmpty: Boolean
        get() = map.isEmpty()

    private val isUSD: Boolean
        get() = currency.code == "USD"

    val currencyCode: String
        get() = currency.code

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
        return RatesEntity(currency, result)
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

    fun convert(token: String, value: Coins): Coins {
        if (currency.code == token || value == Coins.ZERO) {
            return value
        }

        val rate = rateValue(token)
        return (value * rate)
    }

    fun convertFromFiat(token: String, value: Coins): Coins {
        if (currency.code == token) {
            return value
        }

        val rate = rateValue(token)
        return value.div(rate, roundingMode = RoundingMode.HALF_DOWN)
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