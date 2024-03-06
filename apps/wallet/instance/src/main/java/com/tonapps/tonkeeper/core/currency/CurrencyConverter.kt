package com.tonapps.tonkeeper.core.currency

import com.tonapps.tonkeeper.api.to
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import com.tonapps.blockchain.Coin

fun WalletLegacy.currency(fromCurrency: String) = from(fromCurrency, accountId, testnet)

fun WalletLegacy.ton(value: Float) = currency("TON").value(value)

fun WalletLegacy.ton(value: Long) = currency("TON").value(value)

fun from(
    fromCurrency: String,
    accountId: String,
    testnet: Boolean,
): CurrencyConverter {
    return CurrencyConverter(fromCurrency, accountId, testnet)
}

class CurrencyConverter(
    private val fromCurrency: String,
    private val accountId: String,
    private val testnet: Boolean,
) {

    private var value = 0f

    fun value(value: Float) = apply {
        this.value = value
    }

    fun value(value: Long) = apply {
        value(Coin.toCoins(value))
    }

    suspend fun convert(to: String): Float {
        if (fromCurrency == to) {
            return value
        }
        if (0f >= value) {
            return 0f
        }

        return try {
            val rates = CurrencyManager.getInstance().get(accountId, testnet) ?: throw Exception("No rates for account $accountId")
            val token = rates[fromCurrency] ?: throw Exception("No rates for $fromCurrency")
            token.to(to, value)
        } catch (e: Throwable) {
            0f
        }
    }
}