package com.tonapps.tonkeeper.manager.assets

import android.util.Log
import com.tonapps.icu.Coins
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.tonkeeper.core.entities.AssetsEntity.Companion.sort
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AssetsManager(
    private val scope: CoroutineScope,
    private val ratesRepository: RatesRepository,
    private val tokenRepository: TokenRepository,
    private val stakingRepository: StakingRepository,
    private val settingsRepository: SettingsRepository,
) {

    private val cache = TotalBalanceCache()

    init {
        settingsRepository.tokenPrefsChangedFlow.onEach {
            cache.clear()
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
    ): List<AssetsEntity.Token> {
        val safeMode = settingsRepository.safeMode
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet, refresh) ?: return emptyList()
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

    suspend fun getTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency,
        refresh: Boolean = false,
        sorted: Boolean = false,
    ): Coins? {
        if (refresh) {
            val totalBalance = calculateTotalBalance(wallet, currency, true, sorted) ?: return null
            cache.set(wallet, currency, sorted, totalBalance)
            return totalBalance
        }
        var totalBalance = cache.get(wallet, currency, sorted)
        if (totalBalance == null) {
            totalBalance = calculateTotalBalance(wallet, currency, true, sorted) ?: return null
            cache.set(wallet, currency, sorted, totalBalance)
        }
        return totalBalance
    }

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
