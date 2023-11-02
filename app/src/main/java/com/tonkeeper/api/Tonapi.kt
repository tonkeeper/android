package com.tonkeeper.api

import io.tonapi.apis.AccountsApi
import io.tonapi.apis.BlockchainApi
import io.tonapi.apis.ConnectApi
import io.tonapi.apis.DNSApi
import io.tonapi.apis.EmulationApi
import io.tonapi.apis.EventsApi
import io.tonapi.apis.JettonsApi
import io.tonapi.apis.LiteServerApi
import io.tonapi.apis.NFTApi
import io.tonapi.apis.RatesApi
import io.tonapi.apis.StakingApi
import io.tonapi.apis.StorageApi
import io.tonapi.apis.TracesApi
import io.tonapi.apis.WalletApi
import io.tonapi.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ton.console.Network

object Tonapi {

    private const val ENDPOINT = "https://keeper.tonapi.io"

    val accounts = AccountsApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val blockchain = BlockchainApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val connect = ConnectApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val dns = DNSApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val emulation = EmulationApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val events = EventsApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val jettons = JettonsApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val liteServer = LiteServerApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val nft = NFTApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val rates = RatesApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val staking = StakingApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val storage = StorageApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val traces = TracesApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )

    val wallet = WalletApi(
        basePath = ENDPOINT,
        client = Network.okHttpClient
    )
}
