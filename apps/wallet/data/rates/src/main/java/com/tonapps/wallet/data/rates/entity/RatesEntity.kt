package com.tonapps.wallet.data.rates.entity

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import kotlinx.parcelize.Parcelize

@Parcelize
data class RatesEntity(
    val currency: WalletCurrency,
    private val map: HashMap<String, RateEntity>
): Parcelable {

    companion object {
        fun empty(currency: WalletCurrency): RatesEntity {
            return RatesEntity(currency, hashMapOf())
        }
    }

    val isEmpty: Boolean
        get() = map.isEmpty()

    fun merge(rates: RatesEntity) {
        for ((key, value) in rates.map) {
            map[key] = value
        }
    }

    fun merge(rates: List<RateEntity>): RatesEntity {
        for (rate in rates) {
            map[rate.tokenCode] = rate
        }
        return this
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

    fun convert(token: String, value: Coins): Coins {
        if (token == TokenEntity.USDT.address) {
            return value
        }
        val rate = rateValue(token)
        return (value * rate)
    }

    fun convertFromFiat(token: String, value: Coins): Coins {
        if (token == TokenEntity.USDT.address) {
            return value
        }
        val rate = rateValue(token)
        return (value / rate)
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