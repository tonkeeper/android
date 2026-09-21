package com.tonapps.trading.screens.details

import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.BlockchainAddress
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.CoinType
import androidx.lifecycle.viewModelScope
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenButton
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenWalletMode
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.trading.asTokenEntity
import com.tonapps.trading.isTonOrTronChain
import com.tonapps.trading.data.TradingRepository
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.features.events.TxEventUiMapper
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.McEventsRepository
import io.tradingapi.models.AssetCapability
import io.tradingapi.models.AssetDetailsResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import uikit.chart.ChartPeriod
import uikit.chart.ChartPoint
import uikit.extensions.collectFlow

sealed interface AssetDetailsAction : MviAction {
    data object Init : AssetDetailsAction
    data object RefreshBalanceAndHistory : AssetDetailsAction
    data object RealtimeUpdate : AssetDetailsAction
    data object PullToRefresh : AssetDetailsAction
    data class SetChartPeriod(val period: ChartPeriod) : AssetDetailsAction
    data object OpenTokenExplorer : AssetDetailsAction
    data object ToggleVisibility : AssetDetailsAction
    data object ToggleFavorite : AssetDetailsAction
}

sealed interface AssetDetailsEvent {
    data class OpenUrl(val url: String) : AssetDetailsEvent
}

sealed interface AssetDetailsState : MviState {
    data object Loading : AssetDetailsState
    data object Error : AssetDetailsState
    data class Data(
        val details: AssetDetailsResponse,
        val currencyCode: String,
        val sections: AssetDetailsSections,
        val chartData: List<ChartPoint>? = null,
        val chartPeriod: ChartPeriod = AssetDetailsFeature.InitialChartPeriod,
        val isChartLoading: Boolean = false,
        val isChartError: Boolean = false,
        val hiddenBalances: Boolean = false,
        val maxStakingApyFormatted: String? = null,
        val isMultichainWallet: Boolean = false,
        val isHidden: Boolean? = null,
        val isFavorite: Boolean = false,
        val isRefreshing: Boolean = false,
    ) : AssetDetailsState {

        private val capabilities: List<AssetCapability>
            get() = details.capabilities?.capabilities.orEmpty()

        val tradeAvailable: Boolean
            get() = capabilities.contains(AssetCapability.swap)

        val sellToCardAvailable: Boolean
            get() = capabilities.contains(AssetCapability.offramp)

        val buyWithCardAvailable: Boolean
            get() = capabilities.contains(AssetCapability.onramp)
    }
}

class AssetDetailsViewState(
    val global: MviProperty<AssetDetailsState>,
) : MviViewState

@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailsFeature(
    private val tradingRepository: TradingRepository,
    private val tokenRepository: TokenRepository,
    private val accountRepo: McAccountRepository,
    private val accountRepoLegacy: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val eventsRepository: EventsRepository,
    private val mcEventsRepository: McEventsRepository,
    private val stakingRepository: StakingRepository,
    private val txEventUiMapper: TxEventUiMapper,
    private val transactionManager: TransactionManager,
    private val realtimeProvider: McWalletRealtimeProvider,
    private val api: API,
    val assetId: String,
    private val from: AssetScreenFrom,
) : MviFeature<AssetDetailsAction, AssetDetailsState, AssetDetailsViewState>(
    initState = AssetDetailsState.Loading,
    initAction = AssetDetailsAction.Init,
) {

    private val relay = MviRelay<AssetDetailsEvent>()
    val events: Flow<AssetDetailsEvent> = relay.events

    init {
        viewModelScope.launch {
            val walletMode = if (isMultichainWallet(accountRepoLegacy.getSelectedWalletId())) {
                AssetScreenWalletMode.Multi
            } else {
                AssetScreenWalletMode.Single
            }
            AnalyticsHelper.Default.events.assetScreen.assetView(
                from = from,
                asset = assetId,
                walletMode = walletMode,
            )
        }
        collectFlow(
            accountRepoLegacy.selectedWalletFlow.flatMapLatest { wallet ->
                transactionManager.eventsFlow(wallet)
            },
        ) {
            sendAction(AssetDetailsAction.RefreshBalanceAndHistory)
        }
        collectFlow(transactionManager.tronUpdatedFlow) {
            sendAction(AssetDetailsAction.RefreshBalanceAndHistory)
        }
        // Fires once the post-transaction schedule has refetched changed balances into the account
        // cache, so a cache-first rebuild here is enough to bring Sell and Send back after a buy.
        collectFlow(accountRepo.refreshTrigger) {
            sendAction(AssetDetailsAction.RefreshBalanceAndHistory)
        }
        collectFlow(merge(realtimeProvider.balanceHints, realtimeProvider.activityHints)) { walletId ->
            if (walletId == accountRepoLegacy.getSelectedWalletId()) {
                sendAction(AssetDetailsAction.RealtimeUpdate)
            }
        }
    }

    fun trackButtonClick(button: AssetScreenButton) {
        AnalyticsHelper.Default.events.assetScreen.assetButtonClick(
            button = button,
            asset = assetId
        )
    }

    override fun createViewState(): AssetDetailsViewState {
        return buildViewState {
            AssetDetailsViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: AssetDetailsAction) {
        when (action) {
            AssetDetailsAction.Init -> load()
            AssetDetailsAction.RefreshBalanceAndHistory -> refreshBalanceAndHistory()
            AssetDetailsAction.RealtimeUpdate -> {
                mcEventsRepository.invalidateFirstPageCache()
                refreshBalanceAndHistory(forceRefresh = true)
            }
            AssetDetailsAction.PullToRefresh -> refresh()
            is AssetDetailsAction.SetChartPeriod -> {
                val data = obtainSpecificState<AssetDetailsState.Data>() ?: return
                if (data.chartPeriod == action.period && !data.isChartLoading && !data.isChartError) {
                    return
                }
                loadChart(data.details, action.period)
            }

            AssetDetailsAction.OpenTokenExplorer -> {
                val url = getExplorerUrl() ?: return
                relay.emit(AssetDetailsEvent.OpenUrl(url = url))
            }

            AssetDetailsAction.ToggleVisibility -> toggleVisibility()
            AssetDetailsAction.ToggleFavorite -> toggleFavorite()
        }
    }

    private suspend fun load() {
        setState { AssetDetailsState.Loading }
        try {
            val walletId = accountRepoLegacy.getSelectedWalletId()
            val isMultichainWallet = isMultichainWallet(walletId)
            val details = tradingRepository.getAssetDetails(assetId, isMultichainWallet)
            val currencyCode = tradingRepository.getCurrency()
            val tronAddress = if (isMultichainWallet) {
                null
            } else {
                resolveTronAddress(details)
            }
            val tokens = if (!isMultichainWallet && details.asset.isTonOrTronChain()) {
                getAccountTokens(refresh = false, tronAddress = tronAddress)
            } else {
                null
            }
            val accountToken = tokens?.let { findAccountToken(details, it) }
            val account = if (isMultichainWallet) {
                walletId?.let {
                    accountRepo.findAccount(
                        walletId = walletId,
                        assetId = assetId,
                        currencyOverride = currencyCode,
                    )
                }
            } else {
                null
            }
            val recentEvents = if (isMultichainWallet) {
                null
            } else {
                loadTokenTransactions(details, tronAddress)
            }
            val recentActivities = if (isMultichainWallet) {
                loadMcTokenActivities(walletId)
            } else {
                null
            }
            val recentActivitiesWallet = if (isMultichainWallet) {
                walletId?.let { accountRepo.getWallet(it) }
            } else {
                null
            }
            val maxStakingApyFormatted = loadMaxStakingApy(details)
            val isHidden = resolveAssetHidden(details, accountToken, account)
            val isFavorite = tradingRepository.isFavoriteAsset(assetId)
            val sections = AssetDetailsSections.build(
                details = details,
                currencyCode = currencyCode,
                hiddenBalances = settingsRepository.hiddenBalances,
                accountToken = accountToken,
                account = account,
                recentEvents = recentEvents,
                recentActivities = recentActivities,
                recentActivitiesWallet = recentActivitiesWallet,
            )
            setState {
                AssetDetailsState.Data(
                    details = details,
                    currencyCode = currencyCode,
                    sections = sections,
                    chartPeriod = InitialChartPeriod,
                    isChartLoading = true,
                    maxStakingApyFormatted = maxStakingApyFormatted,
                    isHidden = isHidden,
                    isFavorite = isFavorite,
                    isMultichainWallet = isMultichainWallet,
                )
            }
            loadChart(details, InitialChartPeriod)
        } catch (e: Throwable) {
            L.e(e)
            setState { AssetDetailsState.Error }
        }
    }

    private suspend fun refresh() {
        val data = obtainSpecificState<AssetDetailsState.Data>() ?: return
        if (data.isRefreshing) {
            return
        }
        setState<AssetDetailsState.Data> { copy(isRefreshing = true) }
        try {
            fetchDetails()
            try {
                refreshBalanceAndHistory(forceRefresh = true)
            } catch (e: Throwable) {
                L.e(e)
            }
        } finally {
            setState<AssetDetailsState.Data> { copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchDetails() {
        try {
            val isMultichainWallet = isMultichainWallet(accountRepoLegacy.getSelectedWalletId())
            val details = tradingRepository.getAssetDetails(assetId, isMultichainWallet)
            val refreshedApy = loadMaxStakingApy(details)
            setState<AssetDetailsState.Data> {
                copy(details = details, maxStakingApyFormatted = refreshedApy ?: maxStakingApyFormatted)
            }
        } catch (e: Throwable) {
            L.e(e)
        }
    }

    private suspend fun refreshBalanceAndHistory(forceRefresh: Boolean = false) {
        val data = obtainState() as? AssetDetailsState.Data ?: return
        // A cached rebuild would overwrite the fresher sections a pull-to-refresh is about to produce.
        if (!forceRefresh && data.isRefreshing) {
            return
        }
        val details = data.details
        val currencyCode = tradingRepository.getCurrency()
        val walletId = accountRepoLegacy.getSelectedWalletId()
        val isMultichainWallet = isMultichainWallet(walletId)
        val tronAddress = if (isMultichainWallet) {
            null
        } else {
            resolveTronAddress(details)
        }
        val tokensRequested = !isMultichainWallet && details.asset.isTonOrTronChain()
        val tokens = if (tokensRequested) {
            getAccountTokens(refresh = true, tronAddress = tronAddress)
        } else {
            null
        }
        val accountToken = tokens?.let { findAccountToken(details, it) }
        val account = if (isMultichainWallet) {
            walletId?.let {
                accountRepo.findAccount(
                    walletId = walletId,
                    assetId = assetId,
                    currencyOverride = currencyCode,
                    forceRefresh = forceRefresh,
                )
            }
        } else {
            null
        }
        val recentEvents = if (isMultichainWallet) {
            null
        } else {
            loadTokenTransactions(details, tronAddress)
        }
        val recentActivities = if (isMultichainWallet) {
            loadMcTokenActivities(walletId)
        } else {
            null
        }
        val recentActivitiesWallet = if (isMultichainWallet) {
            walletId?.let { accountRepo.getWallet(it) }
        } else {
            null
        }
        val isHidden = resolveAssetHidden(details, accountToken, account)
        val newSections = AssetDetailsSections.build(
            details = details,
            currencyCode = currencyCode,
            hiddenBalances = settingsRepository.hiddenBalances,
            accountToken = accountToken,
            recentEvents = recentEvents,
            recentActivities = recentActivities,
            recentActivitiesWallet = recentActivitiesWallet,
            account = account,
        )
        setState<AssetDetailsState.Data> {
            // sections were built from details captured before suspending; drop them if stale
            if (this.details !== details) {
                return@setState this
            }
            // A pull-to-refresh began while this cached rebuild was in flight — checking at entry
            // only skips rebuilds that had not started yet, so re-check now that we are writing.
            if (isRefreshing && !forceRefresh) {
                return@setState this
            }
            val fetchFailed = tokensRequested && tokens == null
            copy(
                currencyCode = currencyCode,
                sections = if (fetchFailed) {
                    newSections.copy(balance = this.sections.balance)
                } else {
                    newSections
                },
                isHidden = if (fetchFailed) {
                    this.isHidden
                } else {
                    isHidden
                },
                isMultichainWallet = isMultichainWallet,
            )
        }
    }

    private suspend fun loadMaxStakingApy(details: AssetDetailsResponse): String? {
        return try {
            val asset = Asset.coinFromString(details.asset.id)
                ?: return null

            if (asset !is Asset.Coin || asset.chain.coin != CoinType.Gram) {
                return null
            }

            val wallet = accountRepoLegacy.getSelectedWallet()
                ?: return null

            val staking = stakingRepository.get(
                accountId = wallet.accountId,
                network = wallet.network,
                initializedAccount = wallet.initialized,
            )
            val enabledStaking = api.getConfig(wallet.network).enabledStaking
            val maxApy = staking.pools
                .filter { enabledStaking.contains(it.implementation.title) }
                .maxOfOrNull { it.apy }
                ?: return null
            CurrencyFormatter.formatPercent(maxApy).toString()
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun resolveTronAddress(details: AssetDetailsResponse): String? {
        if (details.asset.asTokenEntity.blockchain != Blockchain.TRON) {
            return null
        }
        return try {
            val wallet = accountRepoLegacy.getSelectedWallet() ?: return null
            if (!wallet.hasPrivateKey || wallet.testnet) {
                return null
            }
            accountRepoLegacy.getTronAddress(wallet.id)
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun getAccountTokens(
        refresh: Boolean,
        tronAddress: String?,
    ): List<AccountTokenEntity>? {
        return try {
            val wallet = accountRepoLegacy.requiredSelectedWallet()
            tokenRepository.get(
                currency = settingsRepository.currency,
                accountId = wallet.accountId,
                network = wallet.network,
                refresh = refresh,
                tronAddress = tronAddress,
            ) ?: throw IllegalStateException("Tokens not found for account ${wallet.accountId}")
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private fun findAccountToken(
        details: AssetDetailsResponse,
        tokens: List<AccountTokenEntity>,
    ): AccountTokenEntity? {
        val tokenAddress = details.asset.asTokenEntity.address
        if (tokenAddress.equals(WalletCurrency.TON_CHAIN_KEY, ignoreCase = true)) {
            return tokens.firstOrNull { it.isTon }
        }
        return tokens.firstOrNull { it.address.equalsAddress(tokenAddress) }
    }

    private suspend fun resolveAssetHidden(
        details: AssetDetailsResponse,
        accountToken: AccountTokenEntity?,
        account: AccountWithDetails?,
    ): Boolean? {
        return try {
            val walletId = accountRepoLegacy.getSelectedWalletId() ?: return null
            if (accountRepoLegacy.getWalletById(walletId) != null) {
                // Legacy wallet: the native TON coin can't be hidden.
                if (accountToken == null || accountToken.isTon) {
                    return null
                }
                settingsRepository.getTokenPrefs(
                    walletId = walletId,
                    tokenAddress = accountToken.address,
                    blacklist = accountToken.blacklist,
                ).isHidden
            } else {
                account?.isHidden
            }
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun toggleFavorite() {
        val data = obtainSpecificState<AssetDetailsState.Data>() ?: return
        val previousFavorite = data.isFavorite
        val newFavorite = !previousFavorite
        try {
            setState {
                val d = this as? AssetDetailsState.Data ?: return@setState this
                d.copy(isFavorite = newFavorite)
            }
            if (newFavorite) {
                tradingRepository.addFavoriteAsset(assetId)
            } else {
                tradingRepository.removeFavoriteAsset(assetId)
            }
        } catch (e: Throwable) {
            L.e(e)
            setState {
                val d = this as? AssetDetailsState.Data ?: return@setState this
                d.copy(isFavorite = previousFavorite)
            }
        }
    }

    private suspend fun toggleVisibility() {
        val data = obtainSpecificState<AssetDetailsState.Data>() ?: return
        val currentHidden = data.isHidden ?: return
        val newHidden = !currentHidden
        try {
            val walletId = accountRepoLegacy.getSelectedWalletId() ?: return
            if (accountRepoLegacy.getWalletById(walletId) != null) {
                val tokenAddress = data.details.asset.asTokenEntity.address
                settingsRepository.setTokenHidden(walletId, tokenAddress, newHidden)
            } else {
                accountRepo.setAssetVisibility(walletId, assetId, visible = !newHidden)
            }
            setState {
                val d = this as? AssetDetailsState.Data ?: return@setState this
                d.copy(isHidden = newHidden)
            }
        } catch (e: Throwable) {
            L.e(e)
        }
    }

    private suspend fun loadChart(details: AssetDetailsResponse, period: ChartPeriod) {
        setState {
            val data = this as? AssetDetailsState.Data ?: return@setState this
            data.copy(isChartLoading = true, isChartError = false)
        }
        try {
            val isMultichainWallet = isMultichainWallet(accountRepoLegacy.getSelectedWalletId())
            val chart = tradingRepository.getAssetChart(details.asset.id, period, isMultichainWallet)
            setState {
                val data = this as? AssetDetailsState.Data ?: return@setState this
                data.copy(
                    chartData = chart,
                    chartPeriod = period,
                    isChartLoading = false,
                    isChartError = false,
                )
            }
        } catch (e: Throwable) {
            L.e(e)
            setState {
                val data = this as? AssetDetailsState.Data ?: return@setState this
                data.copy(
                    chartData = emptyList(),
                    chartPeriod = period,
                    isChartLoading = false,
                    isChartError = true,
                )
            }
        }
    }

    private suspend fun loadTokenTransactions(
        details: AssetDetailsResponse,
        tronAddress: String?,
    ): List<AssetDetailsSections.RecentEvents.Item>? {
        if (!details.asset.isTonOrTronChain()) {
            return null
        }
        try {
            val wallet = accountRepoLegacy.getSelectedWallet()
                ?: throw IllegalStateException("Selected wallet not found")
            val token = details.asset.asTokenEntity
            val txEvents = when (token.blockchain) {
                Blockchain.TON -> loadTonTokenTransactions(wallet, token.address)
                Blockchain.TRON -> loadTronTokenTransactions(wallet, tronAddress)
            } ?: return emptyList()
            if (txEvents.isEmpty()) {
                return null
            }
            return txEvents.map {
                AssetDetailsSections.RecentEvents.Item(
                    txEvent = it,
                    uiEvent = txEventUiMapper.toUiItem(it, wallet)
                )
            }
        } catch (e: Throwable) {
            L.e(e)
            return null
        }
    }

    private suspend fun isMultichainWallet(walletId: String?): Boolean {
        if (walletId == null) {
            return false
        }
        return accountRepoLegacy.getWalletById(walletId) == null
    }

    private suspend fun loadMcTokenActivities(walletId: String?): List<HistoryEventEntity>? {
        if (walletId == null) {
            return null
        }
        return try {
            mcEventsRepository.getRecentAssetActivities(
                walletId = walletId,
                assetId = assetId,
            )
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun loadTonTokenTransactions(
        wallet: WalletEntity,
        tokenAddress: String,
    ): List<TxEvent>? {
        val accountEvents = eventsRepository.loadForToken(
            tokenAddress = tokenAddress,
            accountId = wallet.accountId,
            network = wallet.network,
            limit = 4,
        ) ?: return null
        val tonAddress = BlockchainAddress(
            value = wallet.address,
            network = wallet.network,
            blockchain = Blockchain.TON,
        )
        return eventsRepository.mapAccountEventsToTxEvents(
            tonAddress,
            accountEvents.events,
        )
    }

    private suspend fun loadTronTokenTransactions(
        wallet: WalletEntity,
        tronAddress: String?,
    ): List<TxEvent>? {
        val address = tronAddress ?: return null

        val tronEvents = eventsRepository.loadTronEvents(
            tronWalletAddress = address,
            walletId = wallet.id,
            limit = 4,
        ) ?: return null
        val tronBlockchainAddress = BlockchainAddress(
            value = address,
            network = wallet.network,
            blockchain = Blockchain.TRON,
        )
        return eventsRepository.mapTronEventsToTxEvents(
            tronBlockchainAddress,
            tronEvents,
        )
    }

    private suspend fun getExplorerUrl(): String? {
        try {
            val wallet = unifiedAccountRepository.getSelectedWallet()
                ?: return null
            val asset = Asset.coinFromString(assetId)
                ?: return null
            val accountAddress = unifiedAccountRepository.getAccountAddress(wallet, asset.chain)

            return api.getConfig(wallet.network)
                .explorerUrl(asset, accountAddress)
        } catch (e: Throwable) {
            L.e(e)
            return null
        }
    }

    companion object {
        val InitialChartPeriod: ChartPeriod = ChartPeriod.day
    }
}
