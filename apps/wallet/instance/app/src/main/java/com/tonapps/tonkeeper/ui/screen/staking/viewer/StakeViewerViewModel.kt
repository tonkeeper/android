package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.core.components.isNativeTon
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.legacy.enteties.StakedEntity
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.EthenaEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.jettonAssetId
import com.tonapps.wallet.data.multichain.account.jettonToTon
import com.tonapps.wallet.data.multichain.account.matchesJettonMaster
import com.tonapps.wallet.data.multichain.account.toAccountTokenEntity
import com.tonapps.wallet.data.multichain.account.tonCoinAssetId
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.tx.TransactionManager
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
    private val mcAccountRepository: McAccountRepository,
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

    private val _mcLiquidTokenFlow = MutableStateFlow<McLiquidToken?>(null)

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
                    compact = true,
                ),
                fiat = fiat,
                fiatFormat = CurrencyFormatter.formatFiat(currency.code, fiat, compact = true),
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
                    tokenTsUsde.balance.value,
                    compact = true
                ),
                fiat = tokenTsUsde.fiat,
                fiatFormat = CurrencyFormatter.formatFiat(currency.code, tokenTsUsde.fiat, compact = true),
                rate = CurrencyFormatter.formatFiat(
                    currency.code,
                    rates.getRate(tokenTsUsde.address),
                    compact = true
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

    val stakingItemsFlow = combine(poolFlow, _mcLiquidTokenFlow) { (staked, details), mcLiquid ->
        val liquidToken = staked.liquidToken
        val currencyCode = TokenEntity.TON.symbol
        val rates = ratesRepository.getRates(
            wallet.network, currency, listOfNotNull(
                currencyCode, liquidToken?.token?.address
            )
        )

        val amount = staked.balance
        val fiat = if (wallet.type == WalletType.Multichain) {
            staked.fiatBalance
        } else {
            rates.convert(TokenEntity.TON.symbol, amount)
        }

        val apyFormat = CurrencyFormatter.formatPercent(staked.pool.apy)

        val uiItems = mutableListOf<Item>()
        uiItems.add(
            Item.Balance(
                poolImplementation = staked.pool.implementation,
                balance = amount,
                balanceFormat = CurrencyFormatter.format(currencyCode, amount, compact = true),
                fiat = fiat,
                fiatFormat = CurrencyFormatter.formatFiat(currency.code, fiat, compact = true),
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

        if (staked.pendingWithdraw.isPositive) {
            val pendingFiat = rates.convert(TokenEntity.TON.symbol, staked.pendingWithdraw)
            uiItems.add(Item.Space)
            uiItems.add(
                Item.PendingUnstake(
                    balance = staked.pendingWithdraw,
                    balanceFormat = CurrencyFormatter.format(currencyCode, staked.pendingWithdraw, compact = true),
                    fiat = pendingFiat,
                    fiatFormat = CurrencyFormatter.formatFiat(currency.code, pendingFiat, compact = true),
                    hiddenBalance = settingsRepository.hiddenBalances,
                    titleRes = Localization.staking_pending_unstake,
                    subtitleRes = Localization.staking_after_end_of_cycle,
                )
            )
        }

        when {
            mcLiquid != null -> {
                uiItems.add(Item.Space)
                uiItems.add(mcLiquid.toTokenItem())
                uiItems.add(Item.Description(getString(Localization.stake_tonstakers_description)))
                uiItems.add(Item.Space)
            }
            liquidToken != null -> {
                uiItems.add(Item.Space)
                val tokenAddress = liquidToken.token.address
                val rateNow = rates.getRate(tokenAddress)
                val tokenFiat = rates.convert(tokenAddress, liquidToken.value)
                uiItems.add(
                    Item.Token(
                        iconUri = liquidToken.token.imageUri,
                        address = tokenAddress,
                        symbol = liquidToken.token.symbol,
                        name = liquidToken.token.name,
                        balance = liquidToken.value,
                        balanceFormat = CurrencyFormatter.format(
                            liquidToken.token.symbol,
                            liquidToken.value,
                            compact = true
                        ),
                        fiat = tokenFiat,
                        fiatFormat = CurrencyFormatter.formatFiat(currency.code, tokenFiat, compact = true),
                        rate = CurrencyFormatter.formatFiat(currency.code, rateNow, compact = true),
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
        if (wallet.type == WalletType.Multichain) {
            getMultichainData(refresh)
            return
        }

        val tokens =
            tokenRepository.get(currency, wallet.accountId, wallet.network, refresh = refresh)
                ?: return
        _tokensFlow.value = tokens
        _mcLiquidTokenFlow.value = null

        val ethenaData = if (ethenaMethodType != null) {
            tokenRepository.getEthena(wallet.accountId, wallet.id)
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

    private suspend fun getMultichainData(refresh: Boolean) {
        val staking = stakingRepository.get(
            accountId = wallet.accountId,
            network = wallet.network,
            ignoreCache = refresh,
            initializedAccount = wallet.initialized,
        )
        val pool = staking.findPoolByAddress(poolAddress) ?: return
        val details = staking.getDetails(pool.implementation) ?: return

        val pendingWithdraw = staking.getPendingWithdraw(pool)
        val pendingDeposit = staking.getPendingDeposit(pool)
        val readyWithdraw = staking.getReadyWithdraw(pool)

        if (pool.implementation == StakingPool.Implementation.LiquidTF) {
            val liquidAccount = loadLiquidAccount(pool)
            val liquidToken = liquidAccount?.toAccountTokenEntity(currency)
            _tokensFlow.value = listOfNotNull(liquidToken)
            _mcLiquidTokenFlow.value = if (liquidAccount != null && liquidToken != null) {
                McLiquidToken(token = liquidToken, assetId = liquidAccount.asset.id)
            } else {
                null
            }

            val tonBalance = liquidAccount?.toTonBalance() ?: Coins.ZERO
            _poolFlow.value = Pair(
                StakedEntity(
                    pool = pool,
                    balance = tonBalance,
                    readyWithdraw = readyWithdraw,
                    fiatBalance = liquidToken?.fiat ?: Coins.ZERO,
                    fiatReadyWithdraw = Coins.ZERO,
                    liquidToken = liquidToken?.balance,
                    pendingDeposit = pendingDeposit,
                    pendingWithdraw = pendingWithdraw,
                    cycleStart = pool.cycleStart,
                    cycleEnd = pool.cycleEnd,
                ),
                details,
            )
            return
        }

        _tokensFlow.value = emptyList()
        _mcLiquidTokenFlow.value = null

        val amount = staking.getAmount(pool)
        val fiatRates = ratesRepository.getTONRates(wallet.network, currency)
        _poolFlow.value = Pair(
            StakedEntity(
                pool = pool,
                balance = amount,
                readyWithdraw = readyWithdraw,
                fiatBalance = fiatRates.convertTON(amount),
                fiatReadyWithdraw = fiatRates.convertTON(readyWithdraw),
                liquidToken = null,
                pendingDeposit = pendingDeposit,
                pendingWithdraw = pendingWithdraw,
                cycleStart = pool.cycleStart,
                cycleEnd = pool.cycleEnd,
            ),
            details,
        )
    }

    private suspend fun loadLiquidAccount(pool: PoolEntity): AccountWithDetails? {
        val master = pool.liquidJettonMaster ?: return null
        if (pool.implementation != StakingPool.Implementation.LiquidTF) return null
        val assetId = jettonAssetId(master, wallet.testnet)
        val currencyCode = currency.code
        val cached = runCatching {
            mcAccountRepository.getCachedAccounts(
                walletId = wallet.id,
                currency = currencyCode,
                hideDust = settingsRepository.hideDustAssets,
            )
        }.getOrNull()?.accounts?.firstOrNull { it.matchesJettonMaster(master) }

        return cached
            ?: runCatching {
                mcAccountRepository.findAccount(wallet.id, assetId, currencyCode)
            }.getOrNull()
    }

    private suspend fun AccountWithDetails.toTonBalance(): Coins {
        val currencyCode = currency.code
        val tonAccount = runCatching {
            mcAccountRepository.getCachedAccounts(
                walletId = wallet.id,
                currency = currencyCode,
                hideDust = settingsRepository.hideDustAssets,
            )
        }.getOrNull()?.accounts?.firstOrNull { it.asset.isNativeTon() }
            ?: runCatching {
                mcAccountRepository.findAccount(
                    wallet.id,
                    tonCoinAssetId(wallet.testnet),
                    currencyCode,
                )
            }.getOrNull()

        val jettonPrice = rate?.value?.value?.takeIf { it > BigDecimal.ZERO }
        val tonPrice = tonAccount?.rate?.value?.value?.takeIf { it > BigDecimal.ZERO }
        if (jettonPrice == null || tonPrice == null) {
            return Coins.ZERO
        }
        val tonAmount = jettonToTon(displayBalance.value, jettonPrice, tonPrice)
        return Coins.of(java.math.BigDecimal(tonAmount.toPlainString()))
    }

    private fun McLiquidToken.toTokenItem(): Item.Token {
        return Item.Token(
            wallet = wallet,
            iconUri = token.imageUri,
            address = token.address,
            symbol = token.symbol,
            name = token.name,
            balance = token.balance.value,
            balanceFormat = CurrencyFormatter.format(token.symbol, token.balance.value, compact = true),
            fiat = token.fiat,
            fiatFormat = CurrencyFormatter.formatFiat(currency.code, token.fiat, compact = true),
            rate = CurrencyFormatter.formatFiat(currency.code, token.rateNow, compact = true),
            rateDiff24h = token.rateDiff24h,
            verified = token.token.verification == TokenEntity.Verification.whitelist,
            testnet = wallet.testnet,
            hiddenBalance = settingsRepository.hiddenBalances,
            blacklist = token.token.verification == TokenEntity.Verification.blacklist,
            assetId = assetId,
        )
    }

    private data class McLiquidToken(
        val token: AccountTokenEntity,
        val assetId: String,
    )
}