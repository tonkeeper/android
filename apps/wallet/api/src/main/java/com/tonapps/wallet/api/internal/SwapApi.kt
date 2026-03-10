package com.tonapps.wallet.api.internal

import androidx.core.net.toUri
import com.tonapps.network.get
import com.tonapps.network.sse
import com.tonapps.wallet.api.SwapAssetParam
import com.tonapps.wallet.api.entity.SwapEntity
import com.tonapps.wallet.api.withRetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

internal class SwapApi(
    private val okHttpClient: OkHttpClient
) {

    fun getSwapAssets(prefix: String) = withRetry {
        okHttpClient.get("$prefix/v2/swap/assets")
    }

    fun stream(
        prefix: String,
        from: SwapAssetParam,
        to: SwapAssetParam,
        userAddress: String
    ): Flow<SwapEntity.Messages?> {
        if (from.isEmpty && to.isEmpty) {
            return emptyFlow()
        }
        val builder = "$prefix/v2/swap/omniston/stream".toUri().buildUpon()
        from.apply("from", builder)
        to.apply("to", builder)
        builder.appendQueryParameter("userAddress", userAddress)
        val url = builder.build().toString()
        return okHttpClient.sse(url) { }.map {
            SwapEntity.parse(it.data)
        }
    }

}