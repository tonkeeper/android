package com.tonapps.tonkeeper.api.internal.repositories

import android.content.Context
import com.tonapps.tonkeeper.api.base.BaseBlobRepository
import com.tonapps.tonkeeper.core.fiat.models.FiatData
import com.tonapps.wallet.api.API
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Deprecated("Need refactorings")
class FiatMethodsRepository(
    context: Context,
    private val api: API
): BaseBlobRepository<FiatData>("fiat", context) {

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
        val response = runCatching { api.getFiatMethods() }.getOrNull() ?: return@withContext null
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