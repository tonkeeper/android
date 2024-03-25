package com.tonapps.wallet.api.core

import com.tonapps.network.Network
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

class BaseAPI(
    basePath: String
) {

    val accounts: AccountsApi by lazy { AccountsApi(basePath, Network.okHttpClient) }

    val blockchain: BlockchainApi by lazy { BlockchainApi(basePath, Network.okHttpClient) }

    val connect: ConnectApi by lazy { ConnectApi(basePath, Network.okHttpClient) }

    val dns: DNSApi by lazy { DNSApi(basePath, Network.okHttpClient) }

    val emulation: EmulationApi by lazy { EmulationApi(basePath, Network.okHttpClient) }

    val events: EventsApi by lazy { EventsApi(basePath, Network.okHttpClient) }

    val jettons: JettonsApi by lazy { JettonsApi(basePath, Network.okHttpClient) }

    val liteServer: LiteServerApi by lazy { LiteServerApi(basePath, Network.okHttpClient) }

    val nft: NFTApi by lazy { NFTApi(basePath, Network.okHttpClient) }

    val rates: RatesApi by lazy { RatesApi(basePath, Network.okHttpClient) }

    val staking: StakingApi by lazy { StakingApi(basePath, Network.okHttpClient) }

    val storage: StorageApi by lazy { StorageApi(basePath, Network.okHttpClient) }

    val traces: TracesApi by lazy { TracesApi(basePath, Network.okHttpClient) }

    val wallet: WalletApi by lazy { WalletApi(basePath, Network.okHttpClient) }

}