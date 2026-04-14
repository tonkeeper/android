package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.legacy.enteties.StakedEntity
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.EthenaEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StakeViewerViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val poolAddress: String,
    private val ethenaType: String,
    private val stakingRepository: StakingRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
    private val transactionManager: TransactionManager,
) : BaseWalletVM(app) {

    val usdeDisabled: Boolean
        get() = api.getConfig(wallet.network).flags.disableUsde

    private val ethenaMethodType: EthenaEntity.Method.Type? =
        if (ethenaType.isNotEmpty()) EthenaEntity.Method.Type.fromId(ethenaType) else null

    private val currency = settingsRepository.currency
    private val _poolFlow = MutableStateFlow<Pair<StakedEntity, PoolDetailsEntity>?>(null)
    private val poolFlow = _poolFlow.asStateFlow().filterNotNull()

    val poolNameFlow = poolFlow.map { it.first.pool.name }

    private val _ethenaDataFlow = MutableStateFlow<EthenaEntity?>(null)
    private val ethenaDataFlow = _ethenaDataFlow.asStateFlow().filterNotNull()

    private val _tokensFlow = MutableStateFlow<List<AccountTokenEntity>?>(null)
    val tokensFlow = _tokensFlow.asStateFlow().filterNotNull()

    val ethenaItemsFlow = combine(ethenaDataFlow, tokensFlow) { ethena, tokens ->
        val uiItems = mutableListOf<Item>()

        val method = ethena.methods.find { it.type == ethenaMethodType }

        val tokenUsde = tokens.firstOrNull { it.isUSDe }
        val tokenTsUsde = tokens.firstOrNull { it.isTsUSDe } ?: AccountTokenEntity.createEmpty(
            TokenEntity.TS_USDE, wallet.accountId
        )

        if (method == null || tokenUsde == null) {
            return@combine uiItems
        }

        val rates = ratesRepository.getRates(
            wallet.network,
            settingsRepository.currency,
            listOfNotNull(tokenUsde.address, tokenTsUsde.address)
        )

        val balance = rates.convert(
            from = WalletCurrency.TS_USDE_TON_ETHENA,
            value = tokenTsUsde.balance.value,
            to = WalletCurrency.USDE_TON_ETHENA
        )
        val fiat = rates.convert(tokenUsde.address, balance)

        uiItems.add(
            Item.Balance(
                ethenaType = method.type,
                balance = balance,
                balanceFormat = CurrencyFormatter.format(
                    tokenUsde.symbol,
                    balance,
                ),
                fiat = fiat,
                fiatFormat = CurrencyFormatter.formatFiat(currency.code, fiat),
                hiddenBalance = settingsRepository.hiddenBalances,
            )
        )

        if (!usdeDisabled || balance.isPositive) {
            uiItems.add(
                Item.Actions(
                    wallet = wallet,
                    ethenaMethod = method,
                    unstakeDisabled = balance.isZero,
                    stakeDisabled = usdeDisabled,
                )
            )
            uiItems.add(Item.Space)
            uiItems.add(
                Item.EthenaDetails(
                    apyTitle = method.apyTitle,
                    apyDescription = method.apyDescription,
                    apyFormat = CurrencyFormatter.formatPercent(method.apy),
                    bonusApyFormat = method.bonusApy?.let { CurrencyFormatter.formatPercent(it) },
                    bonusTitle = method.bonusTitle,
                    bonusDescription = method.bonusDescription,
                    bonusUrl = method.eligibleBonusUrl,
                    faqUrl = ethena.about.faqUrl,
                )
            )
            uiItems.add(Item.Space)
        }
        uiItems.add(
            Item.Token(
                iconUri = tokenTsUsde.token.imageUri,
                address = tokenTsUsde.address,
                symbol = tokenTsUsde.token.symbol,
                name = tokenTsUsde.token.name,
                balance = tokenTsUsde.balance.value,
                balanceFormat = CurrencyFormatter.format(
                    tokenTsUsde.token.symbol,
                    tokenTsUsde.balance.value
                ),
                fiat = tokenTsUsde.fiat,
                fiatFormat = CurrencyFormatter.formatFiat(currency.code, tokenTsUsde.fiat),
                rate = CurrencyFormatter.formatFiat(
                    currency.code,
                    rates.getRate(tokenTsUsde.address)
                ),
                rateDiff24h = rates.getDiff7d(tokenTsUsde.address),
                verified = tokenTsUsde.token.verification == TokenEntity.Verification.whitelist,
                testnet = wallet.testnet,
                hiddenBalance = settingsRepository.hiddenBalances,
                blacklist = tokenTsUsde.token.verification == TokenEntity.Verification.blacklist,
                wallet = wallet,
            )
        )
        if (!usdeDisabled) {
            uiItems.add(
                Item.Description(
                    description = ethena.about.tsusdeDescription,
                    isEthena = true,
                    uri = ethena.about.faqUrl.toUri()
                )
            )
            uiItems.add(Item.Space)
            uiItems.add(Item.Links(method.links))
        }


        uiItems
    }.flowOn(Dispatchers.IO)

    val stakingItemsFlow = poolFlow.map { (staked, details) ->
        val liquidToken = staked.liquidToken
        val currencyCode = TokenEntity.TON.symbol
        val rates = ratesRepository.getRates(
            wallet.network, currency, listOfNotNull(
                currencyCode, liquidToken?.token?.address
            )
        )

        val amount = staked.balance
        val fiat = rates.convert(TokenEntity.TON.symbol, amount)

        val apyFormat = CurrencyFormatter.formatPercent(staked.pool.apy)

        val uiItems = mutableListOf<Item>()
        uiItems.add(
            Item.Balance(
                poolImplementation = staked.pool.implementation,
                balance = amount,
                balanceFormat = CurrencyFormatter.format(currencyCode, amount),
                fiat = fiat,
                fiatFormat = CurrencyFormatter.formatFiat(currency.code, fiat),
                hiddenBalance = settingsRepository.hiddenBalances,
            )
        )

        val config = api.getConfig(wallet.network)
        val stakingDisabled = !config.enabledStaking.contains(staked.pool.implementation.title) || config.flags.disableStaking

        if (!stakingDisabled || amount.isPositive) {
            uiItems.add(
                Item.Actions(
                    wallet = wallet,
                    poolAddress = poolAddress,
                    unstakeDisabled = amount.isZero,
                    stakeDisabled = stakingDisabled,
                )
            )
        }

        if (liquidToken != null) {
            val tokenAddress = liquidToken.token.address
            val rateNow = rates.getRate(tokenAddress)
            val tokenFiat = rates.convert(tokenAddress, liquidToken.value)
            uiItems.add(Item.Space)
            uiItems.add(
                Item.Token(
                    iconUri = liquidToken.token.imageUri,
                    address = tokenAddress,
                    symbol = liquidToken.token.symbol,
                    name = liquidToken.token.name,
                    balance = liquidToken.value,
                    balanceFormat = CurrencyFormatter.format(
                        liquidToken.token.symbol,
                        liquidToken.value
                    ),
                    fiat = tokenFiat,
                    fiatFormat = CurrencyFormatter.formatFiat(currency.code, tokenFiat),
                    rate = CurrencyFormatter.formatFiat(currency.code, rateNow),
                    rateDiff24h = rates.getDiff7d(tokenAddress),
                    verified = liquidToken.token.verification == TokenEntity.Verification.whitelist,
                    testnet = wallet.testnet,
                    hiddenBalance = settingsRepository.hiddenBalances,
                    blacklist = liquidToken.token.verification == TokenEntity.Verification.blacklist,
                    wallet = wallet,
                )
            )
            uiItems.add(Item.Description(getString(Localization.stake_tonstakers_description)))
            uiItems.add(Item.Space)
        }

        uiItems.add(Item.Space)
        uiItems.add(
            Item.Details(
                apyFormat = "≈ $apyFormat",
                minDepositFormat = if (staked.pool.minStake == Coins.ZERO) "" else CurrencyFormatter.format(
                    currencyCode,
                    staked.pool.minStake
                ),
                maxApy = staked.maxApy
            )
        )
        uiItems.add(Item.Description(getString(Localization.staking_details_description)))
        uiItems.add(Item.Space)
        uiItems.add(Item.Links(details.getLinks(poolAddress)))
        uiItems
    }.flowOn(Dispatchers.IO)

    val uiItemsFlow = if (ethenaMethodType != null) {
        ethenaItemsFlow
    } else {
        stakingItemsFlow
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            getData()
        }
        transactionManager.eventsFlow(wallet).collectFlow {
            getData(true)
        }
    }

    private suspend fun getData(refresh: Boolean = false) {
        val tokens =
            tokenRepository.get(currency, wallet.accountId, wallet.network, refresh = refresh)
                ?: return
        _tokensFlow.value = tokens

        val ethenaData = if (ethenaMethodType != null) {
            tokenRepository.getEthena(wallet.accountId)
        } else {
            null
        }
        ethenaData?.let { _ethenaDataFlow.value = it }

        val staking = stakingRepository.get(wallet.accountId, wallet.network)
        val staked =
            StakedEntity.create(wallet, staking, tokens, currency, ratesRepository, includeEmptyBalances = true)
        val item = staked.find { it.pool.address.equalsAddress(poolAddress) } ?: return
        val details = staking.getDetails(item.pool.implementation) ?: return
        _poolFlow.value = Pair(item, details)
    }

}