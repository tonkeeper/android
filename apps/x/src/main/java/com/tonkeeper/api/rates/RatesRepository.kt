package com.tonkeeper.api.rates

import android.content.Context
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.base.BaseBlobRepository
import com.tonkeeper.api.base.SourceAPI
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.toJSON
import com.tonkeeper.api.withRetry
import io.tonapi.apis.RatesApi
import io.tonapi.models.GetRates200Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.SupportedTokens

class RatesRepository(
    private val context: Context = App.instance,
    private val api: SourceAPI<RatesApi> = Tonapi.rates
): BaseBlobRepository<GetRates200Response>("rates", context) {

    suspend fun sync(
        accountId: String,
        testnet: Boolean,
        jettons: List<String>
    ): GetRates200Response? {
        val tokens = jettons + SupportedTokens.entries.map {
            it.code
        }

        val currency = SupportedCurrency.entries.map {
            it.code
        }

        val response = fromCloud(
            tokens = tokens,
            currency = currency,
            testnet = testnet
        ) ?: return null

        setMemory(accountId, testnet, response)

        saveCache(
            accountId = accountId,
            testnet = testnet,
            blob = toJSON(response)
        )

        return response
    }

    suspend fun get(accountId: String, testnet: Boolean): GetRates200Response? {
        val cache = fromCache(accountId, testnet)
        if (cache != null) {
            return cache
        }
        return sync(accountId, testnet, emptyList())
    }

    private suspend fun fromCloud(
        tokens: List<String>,
        currency: List<String>,
        testnet: Boolean,
    ): GetRates200Response? {
        return fromCloud(
            tokens = tokens.joinToString(","),
            currency = currency.joinToString(","),
            testnet = testnet
        )
    }

    private suspend fun fromCloud(
        tokens: String,
        currency: String,
        testnet: Boolean,
    ): GetRates200Response? = withContext(Dispatchers.IO) {
       withRetry {
           api.get(testnet).getRates(
               tokens = tokens,
               currencies = currency
           )
       }
    }

    override fun onParse(
        blob: String
    ): GetRates200Response {
        return fromJSON(blob)
    }

}