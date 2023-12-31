package com.tonkeeper.api.rates

import android.content.Context
import android.util.Log
import androidx.collection.ArrayMap
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.address
import com.tonkeeper.api.base.BaseBlobRepository
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.toJSON
import com.tonkeeper.api.withRetry
import io.tonapi.apis.RatesApi
import io.tonapi.models.GetRates200Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.SupportedTokens
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RatesRepository(
    private val context: Context = App.instance,
    private val api: RatesApi = Tonapi.rates
): BaseBlobRepository<GetRates200Response>("rates", context) {

    suspend fun sync(
        accountId: String,
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
            currency = currency
        ) ?: return null


        setMemory(accountId, response)

        saveCache(
            accountId = accountId,
            blob = toJSON(response)
        )

        return response
    }

    suspend fun get(accountId: String): GetRates200Response? {
        val cache = fromCache(accountId)
        if (cache != null) {
            return cache
        }
        return sync(accountId, emptyList())
    }

    private suspend fun fromCloud(
        tokens: List<String>,
        currency: List<String>
    ): GetRates200Response? {
        return fromCloud(
            tokens = tokens.joinToString(","),
            currency = currency.joinToString(",")
        )
    }

    private suspend fun fromCloud(
        tokens: String,
        currency: String
    ): GetRates200Response? = withContext(Dispatchers.IO) {
       withRetry {
           api.getRates(
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