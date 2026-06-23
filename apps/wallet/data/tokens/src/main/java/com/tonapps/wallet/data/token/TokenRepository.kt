package com.tonapps.wallet.data.token

import android.content.Context
import androidx.collection.ArrayMap
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.EthenaEntity
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.token.entities.TokenRateEntity
import com.tonapps.wallet.data.token.source.LocalDataSource
import com.tonapps.wallet.data.token.source.RemoteDataSource
import io.tonapi.models.TokenRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class TokenRepository(
    context: Context,
    private val ratesRepository: RatesRepository,
    private val api: API
) {

    sealed interface Keys : CacheKey {
        data object Tokens : Keys
    }

    private val timedCache = TimedCacheMemory<Keys>()

    private val totalBalanceCache = ConcurrentHashMap<String, Coins>(3, 1.0f, 2)

    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api)

    private val ethenaCache = BlobDataSource.simple<EthenaEntity>(context, "ethena")

    fun getToken(accountId: String, network: TonNetwork): TokenEntity? {
        if (accountId.equals("TON", ignoreCase = true)) {
            return TokenEntity.TON
        }

        return remoteDataSource.getJetton(accountId, network)
    }

    suspend fun getTokens(network: TonNetwork, accountIds: List<String>): List<TokenEntity> {
        // TODO add LRU Cache for tokens
//         timedCache.getOrLoad(Keys.Tokens, "${accountIds.size}") {
        return withContext(Dispatchers.IO) {
                if (accountIds.isEmpty()) {
                    return@withContext emptyList()
                }
                val deferredTokens = accountIds.map { accountId ->
                    async { getToken(accountId, network) } // TODO load with batch
                }

                deferredTokens.mapNotNull { it.await() }
            }
//        }
    }

    fun getToken(accountId: String): TokenEntity? {
        return getToken(accountId, TonNetwork.MAINNET) ?: getToken(accountId, TonNetwork.TESTNET)
    }

    suspend fun getToken(accountId: String, network: TonNetwork, tokenAddress: String): TokenEntity? {
        if (accountId.equals("TON", ignoreCase = true)) {
            return TokenEntity.TON
        }
        val token = get(WalletCurrency.USD, accountId, network)?.firstOrNull { token ->
            token.balance.token.address.equalsAddress(tokenAddress)
        }?.balance?.token

        return token ?: getToken(tokenAddress, network)
    }

    suspend fun getTON(
        currency: WalletCurrency,
        accountId: String,
        network: TonNetwork,
        refresh: Boolean = false,
    ): AccountTokenEntity? {
        val tokens = get(currency, accountId, network, refresh) ?: return null
        return tokens.firstOrNull { it.isTon }
    }

    suspend fun getTonBalance(
        currency: WalletCurrency,
        accountId: String,
        network: TonNetwork
    ): Coins? {
        val token = getTON(currency, accountId, network, false) ?: return null
        return token.balance.value
    }

    suspend fun refreshTron(
        accountId: String, network: TonNetwork, tronAddress: String
    ) {
        val tronUsdtBalance = remoteDataSource.loadTronUsdt(tronAddress)
        val tronTrxBalance = remoteDataSource.loadTronTrx(tronAddress)

        val cached = localDataSource.getCache(cacheKey(accountId, network)) ?: return
        val entities = cached.toMutableList()

        run {
            val index = entities.indexOfFirst {
                it.token.address.equalsAddress(TokenEntity.TRON_USDT.address)
            }

            if (index != -1) {
                entities[index] = tronUsdtBalance
            } else if (!api.getConfig(network).flags.disableTron) {
                entities.add(tronUsdtBalance)
            }
        }

        run {
            val index = entities.indexOfFirst {
                it.token.address.equalsAddress(TokenEntity.TRX.address)
            }

            if (index != -1) {
                entities[index] = tronTrxBalance
            } else if (!api.getConfig(network).flags.disableTron) {
                entities.add(tronTrxBalance)
            }
        }

        localDataSource.setCache(cacheKey(accountId, network), entities)
    }

    suspend fun get(
        currency: WalletCurrency,
        accountId: String,
        network: TonNetwork,
        refresh: Boolean = false,
        tronAddress: String? = null,
    ): List<AccountTokenEntity>? {
        if (refresh) {
            return getRemote(currency, accountId, tronAddress, network)
        }
        val tokens = getLocal(currency, accountId, network)
        if (tokens.isNotEmpty()) {
            return tokens
        }
        return getRemote(currency, accountId, tronAddress, network)
    }

    suspend fun mustGet(
        currency: WalletCurrency,
        accountId: String,
        network: TonNetwork,
        refresh: Boolean = false,
    ): List<AccountTokenEntity> {
        return get(currency, accountId, network, refresh) ?: emptyList()
    }

    private suspend fun getRemote(
        currency: WalletCurrency, accountId: String, tronAddress: String?, network: TonNetwork
    ): List<AccountTokenEntity>? = withContext(Dispatchers.IO) {
        val balances = load(currency, accountId, tronAddress, network) ?: return@withContext null
        if (network.isTestnet) {
            return@withContext             buildTokens(
                currency = currency,
                balances = balances,
                fiatRates = RatesEntity.empty(currency),
                network = network
            )
        }

        val fiatRates = ratesRepository.getRates(network, currency, balances.map { it.token.address })
        buildTokens(
            currency = currency, balances = balances, fiatRates = fiatRates, network = network
        )
    }

    suspend fun getLocal(
        currency: WalletCurrency, accountId: String, network: TonNetwork
    ): List<AccountTokenEntity> = withContext(Dispatchers.IO) {
        val balances = cache(accountId, network) ?: return@withContext emptyList()
        if (network.isTestnet) {
            return@withContext             buildTokens(
                currency = currency,
                balances = balances,
                fiatRates = RatesEntity.empty(currency),
                network = network
            )
        }

        val fiatRates = ratesRepository.cache(network, currency, balances.map { it.token.address })

        if (fiatRates.isEmpty) {
            emptyList()
        } else {
            buildTokens(
                currency = currency, balances = balances, fiatRates = fiatRates, network = network
            )
        }
    }

    private fun buildTokens(
        currency: WalletCurrency,
        balances: List<BalanceEntity>,
        fiatRates: RatesEntity,
        network: TonNetwork
    ): List<AccountTokenEntity> {
        val verified = mutableListOf<AccountTokenEntity>()
        val unverified = mutableListOf<AccountTokenEntity>()
        for (balance in balances) {
            val tokenAddress = balance.token.address
            val fiatRate = TokenRateEntity(
                currency = currency,
                fiat = fiatRates.convert(tokenAddress, balance.value),
                rate = fiatRates.getRate(tokenAddress),
                rateDiff24h = fiatRates.getDiff24h(tokenAddress)
            )
            val token = AccountTokenEntity(
                balance = balance, fiatRate = fiatRate
            )
            if (token.verified) {
                verified.add(token)
            } else {
                unverified.add(token)
            }
        }
        if (network.isTestnet) {
            return sortTestnet(verified + unverified)
        }
        return sort(verified) + sort(unverified)
    }

    private fun sort(list: List<AccountTokenEntity>): List<AccountTokenEntity> {
        return list.sortedWith { first, second ->
            when {
                first.isTon -> -1
                second.isTon -> 1
                first.isUsdt -> -1
                second.isUsdt -> 1
                else -> second.fiat.compareTo(first.fiat)
            }
        }
    }

    private fun sortTestnet(list: List<AccountTokenEntity>): List<AccountTokenEntity> {
        return list.sortedWith { first, second ->
            when {
                first.isTon -> -1
                second.isTon -> 1
                else -> second.balance.value.compareTo(first.balance.value)
            }
        }
    }

    private fun cache(
        accountId: String, network: TonNetwork
    ): List<BalanceEntity>? {
        val key = cacheKey(accountId, network)
        return localDataSource.getCache(key)
    }

    private fun updateRates(network: TonNetwork, currency: WalletCurrency, tokens: List<String>) {
        ratesRepository.load(network, currency, tokens.toMutableList())
    }

    private suspend fun load(
        currency: WalletCurrency,
        accountId: String,
        tronAddress: String?,
        network: TonNetwork
    ): List<BalanceEntity>? = withContext(Dispatchers.IO) {
        val tonBalanceDeferred = async { remoteDataSource.loadTON(currency, accountId, network) }
        val jettonsDeferred = async { remoteDataSource.loadJettons(currency, accountId, network) }

        val tronUsdtDeferred = async {
            if (tronAddress != null && network.isMainnet) {
                remoteDataSource.loadTronUsdt(tronAddress)
            } else {
                null
            }
        }

        val tronTrxDeferred = async {
            if (tronAddress != null && network.isMainnet) {
                remoteDataSource.loadTronTrx(tronAddress)
            } else {
                null
            }
        }

        val tonBalance = tonBalanceDeferred.await() ?: return@withContext null
        val jettons = jettonsDeferred.await()?.toMutableList() ?: mutableListOf()

        val tronUsdt = tronUsdtDeferred.await()
        val tronTrx = tronTrxDeferred.await()

        val usdtIndex = jettons.indexOfFirst {
            it.token.address == TokenEntity.USDT.address
        }

        val usdeIndex = jettons.indexOfFirst {
            it.token.address == TokenEntity.USDE.address
        }

        val tsUsdeIndex = jettons.indexOfFirst {
            it.token.address == TokenEntity.TS_USDE.address
        }

        val entities = mutableListOf<BalanceEntity>()
        entities.add(tonBalance)

        if (tronTrx != null && (!api.getConfig(network).flags.disableTron || tronTrx.value.isPositive)) {
            entities.add(tronTrx)
        }

        if (tronUsdt != null && (!api.getConfig(network).flags.disableTron || tronUsdt.value.isPositive)) {
            entities.add(tronUsdt)
        }

        if (usdtIndex == -1 && !network.isTestnet) {
            entities.add(
                BalanceEntity(
                    token = TokenEntity.USDT,
                    value = Coins.ZERO,
                    walletAddress = accountId,
                    initializedAccount = tonBalance.initializedAccount,
                    isRequestMinting = false,
                    isTransferable = true
                )
            )
        } else if (usdtIndex >= 0) {
            jettons[usdtIndex] = jettons[usdtIndex].copy(
                token = TokenEntity.USDT
            )
        }

        val shouldAddUsde = usdeIndex == -1 && (!api.getConfig(network).flags.disableUsde || tsUsdeIndex != -1)

        if (shouldAddUsde && !network.isTestnet) {
            entities.add(
                BalanceEntity(
                    token = TokenEntity.USDE,
                    value = Coins.ZERO,
                    walletAddress = accountId,
                    initializedAccount = tonBalance.initializedAccount,
                    isRequestMinting = false,
                    isTransferable = true
                )
            )
        } else if (usdeIndex >= 0) {
            jettons[usdeIndex] = jettons[usdeIndex].copy(
                token = TokenEntity.USDE
            )
        }

        entities.addAll(jettons)

        updateRates(network, currency, listOf(TokenEntity.TON.symbol))
        bindRates(network, currency, entities)
        localDataSource.setCache(cacheKey(accountId, network), entities)
        totalBalanceCache.remove(cacheKey(accountId, network))
        entities.toList()
    }


    private fun cacheKey(accountId: String, network: TonNetwork): String {
        return "${accountId}_${network.name.lowercase()}"
    }

    private suspend fun bindRates(network: TonNetwork, currency: WalletCurrency, list: List<BalanceEntity>) {
        val rates = ArrayMap<String, TokenRates>()
        for (balance in list) {
            (balance.rates as? TokenRates)?.let {
                rates[balance.token.address] = it
            }
        }
        ratesRepository.insertRates(network, currency, rates)
    }

    suspend fun getEthena(accountId: String, refresh: Boolean = false): EthenaEntity? {
        if (refresh) {
            return getEthenaRemote(accountId)
        }
        val cached = ethenaCache.getCache(accountId)
        return cached ?: getEthenaRemote(accountId)
    }

    suspend fun getEthenaRemote(accountId: String): EthenaEntity? = withContext(Dispatchers.IO) {
        val data = api.getEthena(accountId)
        data?.let {
            ethenaCache.setCache(accountId, data)
        }
        data
    }
}
