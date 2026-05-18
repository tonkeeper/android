package com.tonapps.wallet.api.core

import io.exchangeapi.apis.ExchangeApi
import io.exchangeapi.apis.P2pApi
import io.exchangeapi.apis.SwapApi
import okhttp3.OkHttpClient

class ExchangeAPI(
    basePath: String,
    okHttpClient: OkHttpClient
) {
    val swap: SwapApi by lazy { SwapApi(basePath, okHttpClient) }
    val exchange: ExchangeApi by lazy { ExchangeApi(basePath, okHttpClient) }
    val p2p: P2pApi by lazy { P2pApi(basePath, okHttpClient) }
}
