package com.tonkeeper.api

import com.tonkeeper.api.base.BaseAPI
import com.tonkeeper.api.base.SourceAPI
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
import core.network.Network
import io.tonapi.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex
import ton.TonAddress

object Tonapi {
    private val main = BaseAPI("https://keeper.tonapi.io")
    private val test = BaseAPI("https://testnet.tonapi.io")

    val accounts = SourceAPI(main.accounts, test.accounts)

    val blockchain = SourceAPI(main.blockchain, test.blockchain)

    val connect = SourceAPI(main.connect, test.connect)

    val dns = SourceAPI(main.dns, test.dns)

    val emulation = SourceAPI(main.emulation, test.emulation)

    val events = SourceAPI(main.events, test.events)

    val jettons = SourceAPI(main.jettons, test.jettons)

    val liteServer = SourceAPI(main.liteServer, test.liteServer)

    val nft = SourceAPI(main.nft, test.nft)

    val rates = SourceAPI(main.rates, test.rates)

    val staking = SourceAPI(main.staking, test.staking)

    val storage = SourceAPI(main.storage, test.storage)

    val traces = SourceAPI(main.traces, test.traces)

    val wallet = SourceAPI(main.wallet, test.wallet)

    suspend fun resolveAccount(
        value: String,
        testnet: Boolean
    ): Account? {
        if (testnet) {
            return test.resolveAccount(value)
        }
        return main.resolveAccount(value)
    }
}