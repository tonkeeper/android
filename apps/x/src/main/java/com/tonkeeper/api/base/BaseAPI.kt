package com.tonkeeper.api.base

import core.network.Network
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
import ton.TonAddress

class BaseAPI(
    basePath: String
) {

    val accounts = AccountsApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val blockchain = BlockchainApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val connect = ConnectApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val dns = DNSApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val emulation = EmulationApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val events = EventsApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val jettons = JettonsApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val liteServer = LiteServerApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val nft = NFTApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val rates = RatesApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val staking = StakingApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val storage = StorageApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val traces = TracesApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    val wallet = WalletApi(
        basePath = basePath,
        client = Network.okHttpClient
    )

    suspend fun resolveAccount(
        value: String
    ): Account? = withContext(Dispatchers.IO) {
        try {
            if (!TonAddress.isValid(value)) {
                return@withContext resolveDomain(value.lowercase().trim())
            }
            return@withContext getAccount(value)
        } catch (ignored: Throwable) {}
        return@withContext null
    }

    private fun resolveDomain(
        domain: String,
        suffixList: Array<String> = arrayOf(".ton", ".t.me")
    ): Account? {
        val accountId = domain.lowercase()
        var account: Account? = null
        try {
            account = getAccount(accountId)
        } catch (ignored: Throwable) {}

        for (suffix in suffixList) {
            if (account == null && !accountId.endsWith(suffix)) {
                try {
                    account = getAccount("$accountId$suffix")
                } catch (ignored: Throwable) {}
            }
        }
        if (account?.name == null) {
            account = account?.copy(name = accountId)
        }
        return account
    }

    private fun getAccount(accountId: String): Account {
        return accounts.getAccount(accountId)
    }
}