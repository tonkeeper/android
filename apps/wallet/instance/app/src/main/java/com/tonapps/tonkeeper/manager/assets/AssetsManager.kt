package com.tonapps.tonkeeper.manager.assets

import android.content.Context
import android.util.Log
import com.tonapps.icu.Coins
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.tonkeeper.core.entities.AssetsEntity.Companion.sort
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class AssetsManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ratesRepository: RatesRepository,
    private val tokenRepository: TokenRepository,
    private val stakingRepository: StakingRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val api: API,
) {

    private val cache = TotalBalanceCache(context)

    init {
        settingsRepository.tokenPrefsChangedFlow.drop(1).onEach {
            accountRepository.getSelectedWallet()?.let {
                cache.clear(it, settingsRepository.currency)
            }
        }.launchIn(scope)
    }

    suspend fun getAssets(
        wallet: WalletEntity,
        currency: WalletCurrency = settingsRepository.currency,
        refresh: Boolean,
    ): List<AssetsEntity>? {
        val tokens = getTokens(wallet, currency, refresh)
        val staked = getStaked(wallet, tokens.map { it.token }, currency, refresh)
        val filteredTokens = tokens.filter { !it.token.isLiquid }
        val list = (filteredTokens + staked).sortedBy { it.fiat }.reversed()
        if (list.isEmpty()) {
            return null
        }
        return list
    }

    private suspend fun getTokens(
        wallet: WalletEntity,
        currency: WalletCurrency = settingsRepository.currency,
        refresh: Boolean,
    ): List<AssetsEntity.Token>  {
        val safeMode = settingsRepository.isSafeModeEnabled(api)
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet, refresh) ?: return emptyList()
        tokens.firstOrNull()?.let {
            if (wallet.initialized != it.balance.initializedAccount) {
                accountRepository.setInitialized(wallet.accountId, it.balance.initializedAccount)
            }
        }
        return if (safeMode) {
            tokens.filter { it.verified }.map { AssetsEntity.Token(it) }
        } else {
            tokens.map { AssetsEntity.Token(it) }
        }
    }

    private suspend fun getStaked(
        wallet: WalletEntity,
        tokens: List<AccountTokenEntity>,
        currency: WalletCurrency = settingsRepository.currency,
        refresh: Boolean,
    ): List<AssetsEntity.Staked> {
        val staking = getStaking(wallet, refresh)
        val staked = StakedEntity.create(staking, tokens, currency, ratesRepository)
        return staked.map { AssetsEntity.Staked(it) }
    }

    private suspend fun getStaking(
        wallet: WalletEntity,
        refresh: Boolean
    ): StakingEntity {
        return stakingRepository.get(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            ignoreCache = refresh
        )
    }

    fun getCachedTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean = false,
    ) = cache.get(wallet, currency, sorted)

    suspend fun getRemoteTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean = false,
    ): Coins? {
        val totalBalance = calculateTotalBalance(wallet, currency, true, sorted) ?: return null
        cache.set(wallet, currency, sorted, totalBalance)
        return totalBalance
    }

    fun setCachedTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean = false,
        value: Coins
    ) {
        cache.set(wallet, currency, sorted, value)
    }

    suspend fun getTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean = false
    ) = getCachedTotalBalance(wallet, currency, sorted) ?: getRemoteTotalBalance(wallet, currency, sorted)

    private suspend fun calculateTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency,
        refresh: Boolean,
        sorted: Boolean,
    ): Coins? {
        var assets = getAssets(wallet, currency, refresh) ?: return null
        if (sorted) {
            assets = assets.sort(wallet, settingsRepository)
        }
        return assets.map { it.fiat }.sumOf { it }
    }

}
