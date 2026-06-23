package com.tonapps.wallet.api

import com.tonapps.wallet.api.core.BatteryAPI
import com.tonapps.wallet.api.core.SourceAPI
import okhttp3.OkHttpClient

internal class BatteryProvider(
    mainnetHost: String,
    testnetHost: String,
    tetraHost: String,
    okHttpClient: OkHttpClient,
) {

    private val main = BatteryAPI(mainnetHost, okHttpClient)
    private val test = BatteryAPI(testnetHost, okHttpClient)
    private val tetra = BatteryAPI(tetraHost, okHttpClient)

    val default = SourceAPI(main.default, test.default, tetra.default)

    val emulation = SourceAPI(main.emulation, test.emulation, tetra.emulation)

    val wallet = SourceAPI(main.wallet, test.wallet, tetra.wallet)
}
