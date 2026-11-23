package com.tonapps.wallet.api.core

import com.tonapps.wallet.api.entity.LoyaltyProfileEntity
import com.tonapps.wallet.api.entity.LoyaltyRewardEstimateEntity
import io.Serializer
import io.infrastructure.ApiTags
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class LoyaltyAPI(
    private val basePath: String,
    private val okHttpClient: OkHttpClient,
) {

    fun getProfile(walletId: String): LoyaltyProfileEntity {
        return get("$basePath/profile".toHttpUrl(), walletId)
    }

    fun getRewardEstimate(walletId: String, kind: String, volumeUsd: String): LoyaltyRewardEstimateEntity {
        val url = "$basePath/reward-estimate".toHttpUrl().newBuilder()
            .addQueryParameter("kind", kind)
            .addQueryParameter("volume_usd", volumeUsd)
            .build()
        return get(url, walletId)
    }

    private inline fun <reified T> get(url: HttpUrl, walletId: String): T {
        val request = Request.Builder()
            .url(url)
            .tag(ApiTags.DeviceAuth::class.java, ApiTags.DeviceAuth)
            .tag(ApiTags.WalletAuth::class.java, ApiTags.WalletAuth(walletId))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Loyalty ${url.encodedPath} failed with HTTP ${response.code}")
            }
            return Serializer.fromJSON(response.body.string())
        }
    }
}
