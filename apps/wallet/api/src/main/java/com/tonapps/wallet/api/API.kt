package com.tonapps.wallet.api

import android.content.Context
import android.util.Log
import com.tonapps.blockchain.Coin
import com.tonapps.network.Network
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.api.internal.ConfigRepository
import com.tonapps.wallet.api.internal.InternalApi
import io.tonapi.apis.AccountsApi
import io.tonapi.apis.NFTApi
import io.tonapi.apis.RatesApi
import io.tonapi.apis.WalletApi
import io.tonapi.models.AccountEvent
import io.tonapi.models.NftItem
import io.tonapi.models.TokenRates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import org.ton.api.pub.PublicKeyEd25519

class API(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val internalApi = InternalApi(context)
    private val configRepository = ConfigRepository(context, scope, internalApi)

    val config: ConfigEntity
        get() = configRepository.configEntity

    fun accounts(testnet: Boolean): AccountsApi {
        return Tonapi.accounts.get(testnet)
    }

    fun wallet(testnet: Boolean): WalletApi {
        return Tonapi.wallet.get(testnet)
    }

    fun nft(testnet: Boolean): NFTApi {
        return Tonapi.nft.get(testnet)
    }

    fun rates(): RatesApi {
        return Tonapi.rates.get(false)
    }

    fun getEvents(
        accountId: String,
        testnet: Boolean,
        beforeLt: Long? = null,
        limit: Int = 20
    ): List<AccountEvent> {
        return accounts(testnet).getAccountEvents(
            accountId = accountId,
            limit = limit,
            beforeLt = beforeLt
        ).events
    }

    fun getTonBalance(
        accountId: String,
        testnet: Boolean
    ): BalanceEntity {
        val account = accounts(testnet).getAccount(accountId)
        return BalanceEntity(TokenEntity.TON, Coin.toCoins(account.balance), accountId)
    }

    fun getJettonsBalances(
        accountId: String,
        testnet: Boolean,
        currency: String
    ): List<BalanceEntity> {
        val jettonsBalances = accounts(testnet).getAccountJettonsBalances(
            accountId = accountId,
            currencies = currency
        ).balances
        return jettonsBalances.map { BalanceEntity(it) }.filter { it.value > 0 }
    }

    fun resolveAddressOrName(
        query: String,
        testnet: Boolean
    ): AccountDetailsEntity? {
        return try {
            val account = accounts(testnet).getAccount(query)
            AccountDetailsEntity(query, account, testnet)
        } catch (e: Throwable) {
            null
        }
    }

    fun resolvePublicKey(
        pk: PublicKeyEd25519,
        testnet: Boolean
    ): List<AccountDetailsEntity> {
        return try {
            val query = pk.key.hex()
            val wallets = wallet(testnet).getWalletsByPublicKey(query).accounts
            wallets.map { AccountDetailsEntity(query, it, testnet) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun getRates(currency: String, tokens: List<String>): Map<String, TokenRates> {
        return rates().getRates(tokens.joinToString(","), currency).rates
    }

    fun getNft(address: String, testnet: Boolean): NftItem? {
        return try {
            nft(testnet).getNftItemByAddress(address)
        } catch (e: Throwable) {
            null
        }
    }

    fun getNftItems(address: String, testnet: Boolean): List<NftItem> {
        return accounts(testnet).getAccountNftItems(
            accountId = address,
            limit = 1000,
            indirectOwnership = true,
        ).nftItems
    }

    fun subscribe(accountId: String, testnet: Boolean): Flow<Network.SSEvent> {
        val endpoint = if (testnet) {
            config.tonapiTestnetHost
        } else {
            config.tonapiMainnetHost
        }
        val mempool = Network.subscribe("$endpoint/v2/sse/mempool?accounts=${accountId}")
        val tx = Network.subscribe("$endpoint/v2/sse/accounts/transactions?accounts=${accountId}")
        return merge(mempool, tx)
    }
}