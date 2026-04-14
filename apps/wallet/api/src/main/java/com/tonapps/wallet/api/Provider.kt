package com.tonapps.wallet.api

import com.tonapps.wallet.api.core.BaseAPI
import com.tonapps.wallet.api.core.SourceAPI
import okhttp3.OkHttpClient

internal class Provider(
    mainnetHost: String,
    testnetHost: String,
    tetraHost: String,
    okHttpClient: OkHttpClient,
) {

    private val main = BaseAPI(mainnetHost, okHttpClient)
    private val test = BaseAPI(testnetHost, okHttpClient)
    private val tetra = BaseAPI(tetraHost, okHttpClient)

    val accounts = SourceAPI(main.accounts, test.accounts, tetra.accounts)

    val blockchain = SourceAPI(main.blockchain, test.blockchain, tetra.blockchain)

    val connect = SourceAPI(main.connect, test.connect, tetra.connect)

    val dns = SourceAPI(main.dns, test.dns, tetra.dns)

    val emulation = SourceAPI(main.emulation, test.emulation, tetra.emulation)

    val events = SourceAPI(main.events, test.events, tetra.events)

    val jettons = SourceAPI(main.jettons, test.jettons, tetra.jettons)

    val liteServer = SourceAPI(main.liteServer, test.liteServer, tetra.liteServer)

    val nft = SourceAPI(main.nft, test.nft, tetra.nft)

    val rates = SourceAPI(main.rates, test.rates, tetra.rates)

    val staking = SourceAPI(main.staking, test.staking, tetra.staking)

    val storage = SourceAPI(main.storage, test.storage, tetra.storage)

    val traces = SourceAPI(main.traces, test.traces, tetra.traces)

    val wallet = SourceAPI(main.wallet, test.wallet, tetra.wallet)

    val gasless = SourceAPI(main.gasless, test.gasless, tetra.gasless)

    val utilities = SourceAPI(main.utilities, test.utilities, tetra.utilities)
}