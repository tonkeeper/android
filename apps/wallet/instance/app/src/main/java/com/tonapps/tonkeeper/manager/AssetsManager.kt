package com.tonapps.tonkeeper.manager

import com.tonapps.icu.Coins
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import java.util.concurrent.ConcurrentHashMap

class AssetsManager(
    private val ratesRepository: RatesRepository,
    private val tokenRepository: TokenRepository,
    private val stakingRepository: StakingRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun getAssets(
        wallet: WalletEntity,
        currency: WalletCurrency = settingsRepository.currency,
        refresh: Boolean,
    ): List<AssetsEntity>? {
        val tokens = getTokens(wallet, currency, refresh)
        val staked = getStaked(wallet, tokens.map { it.token }, currency, refresh)

        val liquid = staked.find { it.isTonstakers }?.liquidToken
        val filteredTokens = if (liquid == null) {
            tokens
        } else {
            tokens.filter { !liquid.token.address.contains(it.address)  }
        }
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
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet, refresh) ?: return emptyList()
        return tokens.map { AssetsEntity.Token(it) }
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
        refresh: Boolean = false
    ): Coins {
        val assets = getAssets(wallet, currency, refresh) ?: return Coins.ZERO
        return assets.map { it.fiat }.sumOf { it }
    }
}
