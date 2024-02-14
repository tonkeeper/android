package com.tonapps.tonkeeper.api.base

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
import com.tonapps.network.Network
import ton.TonAddress

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