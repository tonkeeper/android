package com.tonapps.tonkeeper.manager

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

class AssetsManager(
    private val ratesRepository: RatesRepository,
    private val tokenRepository: TokenRepository,
    private val stakingRepository: StakingRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun getAssets(
        wallet: WalletEntity,
        currency: WalletCurrency = settingsRepository.currency
    ): List<AssetsEntity>? {
        val tokens = getTokens(wallet, currency) ?: return null
        val staked = getStaked(wallet, tokens.map { it.token }, currency)
        val liquid = staked.find { it.isTonstakers }?.liquidToken
        val filteredTokens = if (liquid == null) {
            tokens
        } else {
            tokens.filter { !liquid.token.address.contains(it.address)  }
        }
        return (filteredTokens + staked).sortedBy { it.fiat }.reversed()
    }

    private suspend fun getTokens(
        wallet: WalletEntity,
        currency: WalletCurrency = settingsRepository.currency
    ): List<AssetsEntity.Token>? {
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet) ?: return null
        return tokens.map { AssetsEntity.Token(it) }
    }

    private suspend fun getStaked(
        wallet: WalletEntity,
        tokens: List<AccountTokenEntity>,
        currency: WalletCurrency = settingsRepository.currency
    ): List<AssetsEntity.Staked> {
        val staking = getStaking(wallet)
        val staked = StakedEntity.create(staking, tokens, currency, ratesRepository)
        return staked.map { AssetsEntity.Staked(it) }
    }

    private suspend fun getStaking(wallet: WalletEntity): StakingEntity {
        return stakingRepository.get(wallet.accountId, wallet.testnet)
    }
}


/*

        val filteredTokens = if (liquid == null) {
            tokens
        } else {
            tokens.filter { !liquid.token.address.contains(it.address)  }
        }
        if (filteredTokens.isEmpty() && staked.isEmpty()) {
            return null
        }

        val assets = (filteredTokens.map { AssetsEntity.Token(it) } + staked.map {
            AssetsEntity.Staked(it)
        }).sortedBy { it.fiat }.reversed()

        return State.Assets(currency, assets.sort(wallet, settingsRepository), fromCache, fiatRates)
    }
 */