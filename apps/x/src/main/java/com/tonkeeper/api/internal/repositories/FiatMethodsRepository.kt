package com.tonkeeper.api.internal.repositories

import android.content.Context
import com.tonkeeper.api.internal.Tonkeeper
import com.tonkeeper.api.base.BaseBlobRepository
import com.tonkeeper.api.withRetry
import com.tonkeeper.core.fiat.models.FiatData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FiatMethodsRepository(context: Context): BaseBlobRepository<FiatData>("fiat", context) {

    suspend fun get(
        countryCode: String
    ): FiatData? {
        val cache = fromCache(countryCode, false)
        if (cache != null) {
            return cache
        }
        return fromCloud(countryCode)
    }

    private suspend fun fromCloud(
        countryCode: String
    ): FiatData? = withContext(Dispatchers.IO) {
        val response = withRetry {
            Tonkeeper.get("fiat/methods")
        } ?: return@withContext null
        val json = response.getJSONObject("data")
        saveCache(
            accountId = countryCode,
            testnet = false,
            blob = json.toString()
        )

        val data = FiatData(json)
        setMemory(
            accountId = countryCode,
            testnet = false,
            data = data
        )
        return@withContext data
    }

    override fun onParse(blob: String): FiatData {
        return FiatData(blob)
    }
}