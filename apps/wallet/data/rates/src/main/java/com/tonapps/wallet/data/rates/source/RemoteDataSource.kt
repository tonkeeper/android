package com.tonapps.wallet.data.rates.source

import com.tonapps.wallet.api.Tonapi
import com.tonapps.wallet.data.core.WalletCurrency
import io.tonapi.models.TokenRates

internal class RemoteDataSource {

    fun load(currency: WalletCurrency, tokens: List<String>): Map<String, TokenRates> {
        val ratesAPI = Tonapi.rates.get(false)
        return ratesAPI.getRates(tokens.joinToString(","), currency.code).rates
    }
}