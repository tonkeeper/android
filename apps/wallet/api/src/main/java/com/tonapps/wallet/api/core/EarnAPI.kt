package com.tonapps.wallet.api.core

import io.earnapi.apis.VaultsApi
import io.earnapi.apis.WalletsApi
import okhttp3.OkHttpClient

class EarnAPI(
    basePath: String,
    okHttpClient: OkHttpClient
) {
    val vaults: VaultsApi by lazy { VaultsApi(basePath, okHttpClient) }
    val wallets: WalletsApi by lazy { WalletsApi(basePath, okHttpClient) }
}
