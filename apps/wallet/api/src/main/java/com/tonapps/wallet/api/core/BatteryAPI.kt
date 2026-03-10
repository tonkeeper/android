package com.tonapps.wallet.api.core

import io.batteryapi.apis.DefaultApi
import io.batteryapi.apis.EmulationApi
import io.batteryapi.apis.WalletApi
import okhttp3.OkHttpClient

class BatteryAPI(
    basePath: String,
    okHttpClient: OkHttpClient
) {

    val emulation: EmulationApi by lazy { EmulationApi(basePath, okHttpClient) }

    val default: DefaultApi by lazy { DefaultApi(basePath, okHttpClient) }

     val wallet: WalletApi by lazy { WalletApi(basePath, okHttpClient) }
}