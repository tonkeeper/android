package com.tonapps.wallet.data.token

import android.content.Context
import android.util.Log
import androidx.collection.ArrayMap
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
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
    private val context: Context,
    private val ratesRepository: RatesRepository,
    private val api: API
) {

    private val totalBalanceCache = ConcurrentHashMap<String, Coins>(3, 1.0f, 2)

    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api)

    fun getToken(accountId: String, testnet: Boolean): TokenEntity? {
        if (accountId.equals("TON", ignoreCase = true)) {
            return TokenEntity.TON
        }
        return remoteDataSource.getJetton(accountId, testnet)
    }

    suspend fun getTokens(testnet: Boolean, accountIds: List<String>): List<TokenEntity> =
        withContext(Dispatchers.IO) {
            if (accountIds.isEmpty()) {
                return@withContext emptyList()
            }
            val deferredTokens = accountIds.map { accountId ->
                async { getToken(accountId, testnet) }
            }
            deferredTokens.mapNotNull { it.await() }
        }

    fun getToken(accountId: String): TokenEntity? {
        return getToken(accountId, false) ?: getToken(accountId, true)
    }

    suspend fun getToken(accountId: String, testnet: Boolean, tokenAddress: String): TokenEntity? {
        if (accountId.equals("TON", ignoreCase = true)) {
            return TokenEntity.TON
        }
        val token = get(WalletCurrency.USD, accountId, testnet)?.firstOrNull { token ->
            token.balance.token.address.equalsAddress(tokenAddress)
        }?.balance?.token

        return token ?: getToken(tokenAddress, testnet)
    }

    suspend fun getTON(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean,
        refresh: Boolean = false,
    ): AccountTokenEntity? {
        val tokens = get(currency, accountId, testnet, refresh) ?: return null
        return tokens.firstOrNull { it.isTon }
    }


    suspend fun refreshTron(
        accountId: String,
        testnet: Boolean,
        tronAddress: String
    ) {
        val tronUsdtBalance = remoteDataSource.loadTronUsdt(tronAddress)

        val cached = localDataSource.getCache(cacheKey(accountId, false)) ?: return
        val entities = cached.toMutableList()
        val index = entities.indexOfFirst { it.token.address.equalsAddress(TokenEntity.TRON_USDT.address) }

        if (index != -1) {
            entities[index] = tronUsdtBalance
        } else {
            entities.add(tronUsdtBalance)
        }

        localDataSource.setCache(cacheKey(accountId, testnet), entities)
    }

    suspend fun get(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean,
        refresh: Boolean = false,
        tronAddress: String? = null,
    ): List<AccountTokenEntity>? {
        if (refresh) {
            return getRemote(currency, accountId, tronAddress, testnet)
        }
        val tokens = getLocal(currency, accountId, testnet)
        if (tokens.isNotEmpty()) {
            return tokens
        }
        return getRemote(currency, accountId, tronAddress, testnet)
    }

    suspend fun mustGet(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean,
        refresh: Boolean = false,
    ): List<AccountTokenEntity> {
        return get(currency, accountId, testnet, refresh) ?: emptyList()
    }

    private suspend fun getRemote(
        currency: WalletCurrency,
        accountId: String,
        tronAddress: String?,
        testnet: Boolean
    ): List<AccountTokenEntity>? = withContext(Dispatchers.IO) {
        val balances = load(currency, accountId, tronAddress, testnet) ?: return@withContext null
        if (testnet) {
            return@withContext buildTokens(
                currency = currency,
                balances = balances,
                fiatRates = RatesEntity.empty(currency),
                testnet = true
            )
        }

        val fiatRates = ratesRepository.getRates(currency, balances.map { it.token.address })
        buildTokens(
            currency = currency,
            balances = balances,
            fiatRates = fiatRates,
            testnet = false
        )
    }

    suspend fun getLocal(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean
    ): List<AccountTokenEntity> = withContext(Dispatchers.IO) {
        val balances = cache(accountId, testnet) ?: return@withContext emptyList()
        if (testnet) {
            return@withContext buildTokens(
                currency = currency,
                balances = balances,
                fiatRates = RatesEntity.empty(currency),
                testnet = true
            )
        }

        val fiatRates = ratesRepository.cache(currency, balances.map { it.token.address })

        if (fiatRates.isEmpty) {
            emptyList()
        } else {
            buildTokens(
                currency = currency,
                balances = balances,
                fiatRates = fiatRates,
                testnet = false
            )
        }
    }

    private fun buildTokens(
        currency: WalletCurrency,
        balances: List<BalanceEntity>,
        fiatRates: RatesEntity,
        testnet: Boolean
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
                balance = balance,
                fiatRate = fiatRate
            )
            if (token.verified) {
                verified.add(token)
            } else {
                unverified.add(token)
            }
        }
        if (testnet) {
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
        accountId: String,
        testnet: Boolean
    ): List<BalanceEntity>? {
        val key = cacheKey(accountId, testnet)
        return localDataSource.getCache(key)
    }

    private fun updateRates(currency: WalletCurrency, tokens: List<String>) {
        ratesRepository.load(currency, tokens.toMutableList())
    }

    private suspend fun load(
        currency: WalletCurrency,
        accountId: String,
        tronAddress: String?,
        testnet: Boolean
    ): List<BalanceEntity>? = withContext(Dispatchers.IO) {
        val tonBalanceDeferred = async { remoteDataSource.loadTON(currency, accountId, testnet) }
        val jettonsDeferred = async { remoteDataSource.loadJettons(currency, accountId, testnet) }
        val tronUsdtDeferred = async {
            if (tronAddress != null && !testnet) {
                remoteDataSource.loadTronUsdt(tronAddress)
            } else {
                null
            }
        }

        val tonBalance = tonBalanceDeferred.await() ?: return@withContext null

        val jettons = jettonsDeferred.await()?.toMutableList() ?: mutableListOf()

        val tronUsdt = tronUsdtDeferred.await()

        val usdtIndex = jettons.indexOfFirst {
            it.token.address == TokenEntity.USDT.address
        }

        val entities = mutableListOf<BalanceEntity>()
        entities.add(tonBalance)

        if (tronUsdt != null) {
            entities.add(tronUsdt)
        }

        if (usdtIndex == -1 && !testnet) {
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

        entities.addAll(jettons)

        updateRates(currency, listOf(TokenEntity.TON.symbol))
        bindRates(currency, entities)
        localDataSource.setCache(cacheKey(accountId, testnet), entities)
        totalBalanceCache.remove(cacheKey(accountId, testnet))
        entities.toList()
    }

    private fun cacheKey(accountId: String, testnet: Boolean): String {
        if (!testnet) {
            return accountId
        }
        return "${accountId}_testnet"
    }

    private suspend fun bindRates(currency: WalletCurrency, list: List<BalanceEntity>) {
        val rates = ArrayMap<String, TokenRates>()
        for (balance in list) {
            balance.rates?.let {
                rates[balance.token.address] = it
            }
        }
        ratesRepository.insertRates(currency, rates)
    }
}