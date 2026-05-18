package com.tonapps.tonkeeper.manager.assets

import android.content.Context
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.icu.Coins
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.legacy.enteties.StakedEntity
import com.tonapps.tonkeeper.core.sort
import com.tonapps.tonkeeper.core.sumOfVerifiedFiat
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.token.entities.TokenRateEntity
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
) : EmulationUseCase.Delegate {

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
        var staked = getStaked(wallet, tokens.map { it.token }, currency, refresh)

        val filteredTokens = tokens.filter { !it.token.isLiquid && !it.token.isUSDe }.toMutableList()
        val tokenTsUsde =
            tokens.firstOrNull { it.token.address.equalsAddress(TokenEntity.TON_TS_USDE) }?.token
        val tokenUsde =
            tokens.firstOrNull { it.token.address.equalsAddress(TokenEntity.TON_USDE) }?.token
        tokenUsde?.let {
            if (tokenTsUsde != null) {
                val rates = ratesRepository.getRates(
                    wallet.network,
                    currency,
                    listOf(it.address, tokenTsUsde.address)
                )
                val stakedUsde = rates.convert(
                    from = WalletCurrency.TS_USDE_TON_ETHENA,
                    value = tokenTsUsde.balance.value,
                    to = WalletCurrency.USDE_TON_ETHENA
                )
                val balance =
                    it.balance.copy(value = it.balance.value + stakedUsde)
                filteredTokens.add(
                    AssetsEntity.Token(
                        token = it.copy(
                            balance = balance,
                            fiatRate = TokenRateEntity(
                                currency = currency,
                                fiat = rates.convert(it.address, balance.value),
                                rate = rates.getRate(it.address),
                                rateDiff24h = rates.getDiff24h(it.address)
                            )
                        )
                    )
                )
            } else {
                filteredTokens.add(AssetsEntity.Token(token = it))
            }
        }
        val list = (filteredTokens + staked).sortedBy { it.fiat }.reversed()
        if (list.isEmpty()) {
            return null
        }
        return list
    }

    suspend fun getToken(
        wallet: WalletEntity, token: String, currency: WalletCurrency = settingsRepository.currency
    ): AssetsEntity.Token? {
        val tokens = getTokens(wallet, currency, false)
        return tokens.firstOrNull {
            it.token.address.equalsAddress(token)
        }
    }

    suspend fun getTokens(
        wallet: WalletEntity,
        accountIds: List<String>,
        currency: WalletCurrency = settingsRepository.currency
    ): List<AssetsEntity.Token> = withContext(Dispatchers.IO) {
        if (accountIds.isEmpty()) {
            emptyList()
        } else {
            val tokens = getTokens(wallet, currency, false)
            tokens.filter {
                accountIds.any { accountId -> it.token.address.equalsAddress(accountId) }
            }
        }
    }

    suspend fun getTokens(
        wallet: WalletEntity,
        currency: WalletCurrency = settingsRepository.currency,
        refresh: Boolean,
    ): List<AssetsEntity.Token> {
        val safeMode = settingsRepository.isSafeModeEnabled(wallet.network)
        val tronAddress =
            if (wallet.hasPrivateKey && !wallet.testnet) {
                accountRepository.getTronAddress(wallet.id)
            } else null
        val tokens =
            tokenRepository.get(currency, wallet.accountId, wallet.network, refresh, tronAddress)
                ?: return emptyList()
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
        val staked = StakedEntity.create(wallet, staking, tokens, currency, ratesRepository)
        return staked.map { AssetsEntity.Staked(it) }
    }

    private suspend fun getStaking(
        wallet: WalletEntity, refresh: Boolean
    ): StakingEntity {
        return stakingRepository.get(
            accountId = wallet.accountId, network = wallet.network, ignoreCache = refresh
        )
    }

    fun getCachedTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean = false,
    ) = cache.get(wallet, currency, sorted)

    suspend fun requestTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency,
        refresh: Boolean = false,
        sorted: Boolean = false,
    ): Coins? {
        val totalBalance = calculateTotalBalance(wallet, currency, refresh, sorted) ?: return null
        cache.set(wallet, currency, sorted, totalBalance)
        return totalBalance
    }

    fun setCachedTotalBalance(
        wallet: WalletEntity, currency: WalletCurrency, sorted: Boolean = false, value: Coins
    ) {
        cache.set(wallet, currency, sorted, value)
    }

    suspend fun getTotalBalance(
        wallet: WalletEntity, currency: WalletCurrency, sorted: Boolean = false
    ) = getCachedTotalBalance(wallet, currency, sorted) ?: requestTotalBalance(
        wallet, currency, sorted
    )

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
        return assets.sumOfVerifiedFiat()
    }

    override suspend fun getTotalBalance(
        wallet: WalletEntity,
        currency: WalletCurrency
    ): Coins? {
        return getTotalBalance(wallet, currency, false)
    }
}
