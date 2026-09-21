package com.tonapps.wallet.api.core

import io.walletapi.apis.AssetsApi
import io.walletapi.apis.AuthApi
import io.walletapi.apis.BroadcastApi
import io.walletapi.apis.FeesApi
import io.walletapi.apis.NodesApi
import io.walletapi.apis.RafflesApi
import io.walletapi.apis.SystemApi
import io.walletapi.apis.WalletsApi
import okhttp3.OkHttpClient

class MultichainAPI(
    basePath: String,
    okHttpClient: OkHttpClient
) {
    val wallets: WalletsApi by lazy { WalletsApi(basePath, okHttpClient) }
    val auth: AuthApi by lazy { AuthApi(basePath, okHttpClient) }
    val assets: AssetsApi by lazy { AssetsApi(basePath, okHttpClient) }
    val broadcast: BroadcastApi by lazy { BroadcastApi(basePath, okHttpClient) }
    val fees: FeesApi by lazy { FeesApi(basePath, okHttpClient) }
    val nodes: NodesApi by lazy { NodesApi(basePath, okHttpClient) }
    val raffles: RafflesApi by lazy { RafflesApi(basePath, okHttpClient) }
    val system: SystemApi by lazy { SystemApi(basePath, okHttpClient) }
}
