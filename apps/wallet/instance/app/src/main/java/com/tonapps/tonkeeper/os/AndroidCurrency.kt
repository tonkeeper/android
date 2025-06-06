package com.tonapps.tonkeeper.os

import android.icu.util.Currency
import com.tonapps.uikit.flag.R
import com.tonapps.uikit.flag.getFlagDrawable
import java.util.Locale

data class AndroidCurrency(
    val currency: Currency,
    val locale: Locale,
    private val iconRes: Int
) {

    companion object {

        private val popularCurrencies = listOf(
            "USD",
            "EUR",
            "GBP",
            "JPY",
            "CHF",
            "CNY",
            "INR",
            "UAH",
            "RUB",
            "AUD",
            "CAD",
            "HKD",
            "SGD"
        )

        fun sort(value: List<AndroidCurrency>): List<AndroidCurrency> {
            return value.sortedWith(compareBy { currency ->
                val index = popularCurrencies.indexOf(currency.currencyCode)
                if (index >= 0) index else Int.MAX_VALUE
            })
        }

        val all: List<AndroidCurrency> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Locale.getAvailableLocales().mapNotNull(::create).distinctBy {
                it.code
            }.sortedWith(compareBy({ currency ->
                val index = popularCurrencies.indexOf(currency.currencyCode)
                if (index >= 0) index else Int.MAX_VALUE
            }, { currency ->
                currency.currencyCode
            }))
        }

        private fun create(locale: Locale): AndroidCurrency? {
            val currency = try {
                Currency.getInstance(locale)
            } catch (e: Throwable) {
                return null
            }
            val iconRes = getFlagDrawable(locale.country) ?: return null
            return AndroidCurrency(currency, locale, iconRes)
        }

        fun resolve(code: String): AndroidCurrency? {
            return resolveBySign(code) ?: resolveByCountry(code)
        }

        fun resolveBySign(code: String): AndroidCurrency? {
            return all.firstOrNull { currency ->
                currency.currencyCode.equals(code, true)
            }
        }

        fun resolveByCountry(code: String): AndroidCurrency? {
            val locale = try {
                Locale("", code)
            } catch (e: Throwable) {
                return null
            }
            return all.firstOrNull { currency ->
                currency.locale.country.equals(locale.country, true)
            }
        }
    }

    val currencyCode: String
        get() = currency.currencyCode

    val countryCode: String
        get() = locale.country

    val displayName: String
        get() = currency.displayName

    val symbol: String
        get() = currency.symbol

    val code: String
        get() = currency.currencyCode

    val icon: Int
        get() {
            if (currencyCode == "EUR") {
                return R.drawable.eu
            }
            return iconRes
        }

}