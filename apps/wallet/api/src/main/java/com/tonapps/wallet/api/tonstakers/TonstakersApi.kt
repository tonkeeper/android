package com.tonapps.wallet.api.tonstakers

import com.tonapps.network.get
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class TonstakersApi(
    private val okHttpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getRewards(
        address: String,
        fromSeconds: Long?,
        limit: Int,
        offset: Int,
        includeTotal: Boolean,
    ): TonstakersRewardsResponse {
        val url = HOST.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/tasks/rewards")
            .addQueryParameter("address", address)
            .addQueryParameter("kind", "reward")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("total", includeTotal.toString())
        if (fromSeconds != null) {
            url.scopeItemsAndTotalFrom(fromSeconds)
        }
        return json.decodeFromString(okHttpClient.get(url.build().toString()))
    }

    fun getUpcomingRewards(address: String): TonstakersUpcomingRewardsResponse {
        val url = HOST.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/tasks/upcoming-rewards")
            .addQueryParameter("address", address)
        return json.decodeFromString(okHttpClient.get(url.build().toString()))
    }

    private fun HttpUrl.Builder.scopeItemsAndTotalFrom(fromSeconds: Long) {
        addQueryParameter("from", fromSeconds.toString())
        addQueryParameter("from_total", fromSeconds.toString())
    }

    private companion object {
        const val HOST = "https://api.tonstakers.com"
    }
}
