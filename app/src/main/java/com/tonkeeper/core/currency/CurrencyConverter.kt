package com.tonkeeper.core.currency

import android.util.Log
import com.tonkeeper.App
import com.tonkeeper.api.to
import com.tonkeeper.core.Coin
import io.tonapi.models.TokenRates
import org.ton.block.AddrStd
import ton.SupportedCurrency
import ton.SupportedTokens

fun from(
    fromCurrency: String,
    accountId: String
): CurrencyConverter {
    return CurrencyConverter(fromCurrency, accountId)
}

fun from(
    fromCurrency: SupportedTokens,
    accountId: String
): CurrencyConverter {
    return from(fromCurrency.code, accountId)
}

class CurrencyConverter(
    private val fromCurrency: String,
    private val accountId: String,
) {

    private var value = 0f

    fun value(value: Float) = apply {
        this.value = value
    }

    fun value(value: Long) = apply {
        value(Coin.toCoins(value))
    }

    suspend fun to(to: SupportedCurrency = App.settings.currency): Float {
        return to(to.code)
    }

    suspend fun to(to: String): Float {
        if (fromCurrency == to) {
            return value
        }
        if (0f >= value) {
            return 0f
        }

        return try {
            val rates = CurrencyManager.getInstance().get(accountId) ?: throw Exception("No rates for account $accountId")
            val token = rates[fromCurrency] ?: throw Exception("No rates for $fromCurrency")
            token.to(to, value)
        } catch (e: Throwable) {
            Log.d("CurrencyConverterLog", "Error converting currenc", e)
            0f
        }
    }
}