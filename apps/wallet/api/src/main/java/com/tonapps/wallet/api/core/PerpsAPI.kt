package com.tonapps.wallet.api.core

import io.perpsapi.apis.PositionsApi
import io.perpsapi.apis.ScreensApi
import okhttp3.OkHttpClient

class PerpsAPI(
    basePath: String,
    okHttpClient: OkHttpClient
) {
    val screens: ScreensApi by lazy { ScreensApi(basePath, okHttpClient) }
    val positions: PositionsApi by lazy { PositionsApi(basePath, okHttpClient) }
}
