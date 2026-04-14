package com.tonapps.wallet.api.core

import io.tradingapi.apis.ShelvesApi
import okhttp3.OkHttpClient

class TradingAPI(
    basePath: String,
    okHttpClient: OkHttpClient
) {
    val shelves: ShelvesApi by lazy { ShelvesApi(basePath, okHttpClient) }
}
