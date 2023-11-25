package com.tonkeeper.api.internal.repositories

import android.content.Context
import com.tonkeeper.api.internal.Tonkeeper
import com.tonkeeper.api.base.BaseBlobRepository
import com.tonkeeper.core.fiat.models.FiatData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FiatMethodsRepository(context: Context): BaseBlobRepository<FiatData>("fiat", context) {

    suspend fun get(
        countryCode: String
    ): FiatData {
        val cache = fromCache(accountId = countryCode)
        if (cache != null) {
            return cache
        }
        return fromCloud(countryCode)
    }

    private suspend fun fromCloud(
        countryCode: String
    ): FiatData = withContext(Dispatchers.IO) {
        val json = Tonkeeper.get("fiat/methods").getJSONObject("data")
        saveCache(
            accountId = countryCode,
            blob = json.toString()
        )

        val data = FiatData(json)
        setMemory(
            accountId = countryCode,
            data = data
        )
        return@withContext data
    }

    override fun onParse(blob: String): FiatData {
        return FiatData(blob)
    }
}