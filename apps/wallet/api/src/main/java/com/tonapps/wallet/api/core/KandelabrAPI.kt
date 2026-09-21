package com.tonapps.wallet.api.core

import io.kandelabrapi.apis.CandlesApi
import okhttp3.OkHttpClient

class KandelabrAPI(
    basePath: String,
    okHttpClient: OkHttpClient
) {
    val candles: CandlesApi by lazy { CandlesApi(basePath, okHttpClient) }
}
