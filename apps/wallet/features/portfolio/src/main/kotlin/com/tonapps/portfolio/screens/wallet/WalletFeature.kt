package com.tonapps.portfolio.screens.wallet

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map as mapPaging
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.chainkit.core.chain.model.num.CoinValue
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.core.flags.Features
import com.tonapps.core.flags.WalletFeature.Earn
import com.tonapps.core.flags.WalletFeature.Perps
import com.tonapps.core.helper.EarnAnalytics
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.extensions.fiatSymbol
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.flow.flatMapLatestCatching
import com.tonapps.network.NetworkMonitor
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsPortfolioSnapshot
import com.tonapps.perps.data.PerpsPortfolioStore
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.formatFiatAmount
import com.tonapps.perps.screens.portfolio.toUi
import com.tonapps.wallet.data.earn.EarnRepository
import com.tonapps.core.paging.AccountsPagingCacheSession
import com.tonapps.core.paging.AccountsPagingSource
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.banner.BannerRepository
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.core.Trust
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.WALLET_ASSETS_PAGE_SIZE
import com.tonapps.wallet.data.multichain.account.WalletPerpsTotal
import com.tonapps.wallet.data.multichain.account.walletAssetsFilters
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.tx.TransactionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal as JavaBigDecimal
import java.time.Duration
import java.time.OffsetDateTime

data class WalletTotal(
    val walletId: String,
    val currencyCode: String,
    val currency: FiatCurrency,
    val value: CoinValue,
    val perps: BigDecimal?,
)

sealed interface WalletTotalUi {
    data object HiddenBalance : WalletTotalUi
    data class Amount(val total: WalletTotal) : WalletTotalUi
}

data class WalletAccountItem(
    val account: AccountWithDetails,
    val hiddenBalance: Boolean,
)

data class WalletBattery(
    val level: Float,
    val viewed: Boolean,
    val negative: Boolean,
)

enum class WalletAction {
    Send, Deposit, Swap, Stake
}

class WalletFeature(
    val accountRepo: McAccountRepository,
    val accountRepoLegacy: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val batteryRepository: BatteryRepository,
    private val backupRepository: BackupRepository,
    private val bannerRepository: BannerRepository,
    private val raffleRepository: RaffleRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val stakingRepository: StakingRepository,
    private val networkMonitor: NetworkMonitor,
    private val transactionManager: TransactionManager,
    private val api: API,
    private val pushStatusProvider: PushStatusProvider,
    private val migrationStatusProvider: MigrationStatusProvider,
    private val biometryStatusProvider: BiometryStatusProvider,
    private val perpsPortfolioStore: PerpsPortfolioStore,
    private val perpsRepository: PerpsRepository,
    private val earnRepository: EarnRepository,
    private val appLifecycle: AppLifecycleProvider,
) : AsyncViewModel() {

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val balanceRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val accountsPagingCacheSession = AccountsPagingCacheSession()
    private val perpsLive = perpsPortfolioStore.liveSubscription()

    private val errorRelay = MviRelay<Throwable>()
    val error: Flow<Throwable> = errorRelay.events

    val wallet = combine(
        accountRepoLegacy.selectedWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        accountRepo.refreshTrigger,
    ) { selected, _, _ ->
        accountRepo.getWallet(selected.id)
    }.cacheState()

    val settingsBadge = combine(wallet, backupRepository.stream) { mcWallet, backups ->
        mcWallet != null && backups.none { it.walletId == mcWallet.id }
    }.cacheState(initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val tonAddress: StateFlow<String?> = wallet
        .mapNotNull { it?.id }
        .distinctUntilChanged()
        .mapLatest { walletId -> loadTonAddress(walletId) }
        .cacheState()

    val pushEnabling: StateFlow<Boolean> field = MutableStateFlow(false)

    private val hiddenBalances: StateFlow<Boolean> = settingsRepository.hiddenBalancesFlow
        .map { settingsRepository.hiddenBalances }
        .cacheState(initialValue = settingsRepository.hiddenBalances)

    private val assetsTotal = MutableStateFlow<WalletTotal?>(null)
    private var totalsRefresh: Job? = null

    val isTotalStale: StateFlow<Boolean> field = MutableStateFlow(true)

    private val selectedWalletId = accountRepoLegacy.selectedWalletFlow.map { it.id }

    // Finalized on-chain events of the selected wallet (SSE); each one changes balances and may
    // have granted raffle tickets server-side, so it refreshes both the assets and the raffles.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val onChainEvents = accountRepoLegacy.selectedWalletFlow
        .flatMapLatest { transactionManager.eventsFlow(it) }
        .filter { !it.pending }

    private val walletIdRefresh = merge(
        selectedWalletId,
        refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId() },
        onChainEvents.mapNotNull { accountRepoLegacy.getSelectedWalletId() },
    )

    private val setupRefresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val raffles: StateFlow<List<RaffleEntity>> = merge(
        selectedWalletId.map { it to false },
        refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
        onChainEvents.mapNotNull { accountRepoLegacy.getSelectedWalletId() }.map { it to true },
    )
        .flatMapLatestCatching { (walletId, forced) ->
            raffleRepository.getRafflesFlow(walletId, forced)
        }
        .cacheState(initialValue = emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val raffleRows: StateFlow<List<RaffleEntity>> = raffles
        .transformLatest { raffles ->
            while (true) {
                val now = OffsetDateTime.now()
                emit(raffles.filter { it.hasStarted(now) && !it.isOfferPhase })
                val nextBoundary = raffles
                    .map { it.startsAt }
                    .filter { it.isAfter(now) }
                    .minOrNull()
                    ?: break
                delay(Duration.between(OffsetDateTime.now(), nextBoundary).toMillis().coerceAtLeast(0))
            }
        }
        .cacheState(initialValue = emptyList())

    // The wallet screen gets its raffle banner through the shared banners feed
    // (backend inserts it there); the raffle endpoint's own banner feeds other
    // surfaces, e.g. the trading screen.
    @OptIn(ExperimentalCoroutinesApi::class)
    val banners: StateFlow<List<BannerEntity>> = walletIdRefresh
        .flatMapLatest { walletId ->
            bannerRepository.getBannersFlow(walletId, isMultichain = true)
        }
        .cacheState(initialValue = emptyList())

    val actions = listOf(
        WalletAction.Send,
        WalletAction.Deposit,
        WalletAction.Swap,
        WalletAction.Stake,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accounts: Flow<PagingData<AccountWithDetails>> = combine(
        merge(walletIdRefresh, balanceRefreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId() }),
        settingsRepository.currencyFlow,
        settingsRepository.safeModeChangedFlow,
        settingsRepository.hideDustAssetsFlow,
    ) { walletId, walletCurrency, _, _ ->
        walletId to walletCurrency.code
    }
        .flatMapLatest { (walletId, currencyCode) ->
            val previous = assetsTotal.value
            if (previous != null &&
                (previous.walletId != walletId || previous.currencyCode != currencyCode)
            ) {
                assetsTotal.value = null
                isTotalStale.value = true
            }
            val filters = settingsRepository.walletAssetsFilters(walletId)
            Pager(
                config = PagingConfig(
                    initialLoadSize = WALLET_ASSETS_PAGE_SIZE,
                    pageSize = WALLET_ASSETS_PAGE_SIZE,
                    prefetchDistance = 5,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = {
                    AccountsPagingSource(
                        accountRepo = accountRepo,
                        walletId = walletId,
                        currency = currencyCode,
                        verifiedOnly = filters.verifiedOnly,
                        hideDust = filters.hideDust,
                        cacheSession = accountsPagingCacheSession,
                        scope = viewModelScope,
                        onTotalLoaded = { result ->
                            assetsTotal.update { previous ->
                                WalletTotal(
                                    walletId = walletId,
                                    currencyCode = currencyCode,
                                    currency = FiatCurrency(currencyCode.fiatSymbol()),
                                    value = DisplayUnit(result.total, Decimal(2)),
                                    perps = result.perpsTotal.resolve(previous.perpsOf(walletId, currencyCode)),
                                )
                            }
                        },
                        onNetworkConfirmed = { confirmTotal(walletId, currencyCode) },
                        onLoadError = { error -> handleRefreshError(error) },
                    )
                },
            ).flow.map { pagingData ->
                if (filters.verifiedOnly) {
                    pagingData.filter { it.asset.verification.isVerified }
                } else {
                    pagingData
                }
            }
        }
        .cachedIn(viewModelScope)

    // Mapped after cachedIn: a toggle re-maps the cached pages instead of reloading them.
    val accountsFlow: Flow<PagingData<WalletAccountItem>> = combine(accounts, hiddenBalances) { pagingData, hiddenBalance ->
        pagingData.mapPaging { WalletAccountItem(account = it, hiddenBalance = hiddenBalance) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadedCollectibles: StateFlow<WalletCollectiblesState?> = combine(
        walletIdRefresh,
        settingsRepository.tokenPrefsChangedFlow,
        settingsRepository.safeModeChangedFlow,
        networkMonitor.isOnlineFlow,
    ) { walletId, _, _, isOnline ->
        collectiblesFlow(walletId, isOnline)
    }.flatMapLatest { it }.cacheState(initialValue = null)

    val collectibles: StateFlow<WalletCollectiblesState?> = combine(
        loadedCollectibles,
        hiddenBalances,
    ) { collectibles, hiddenBalance ->
        collectibles?.copy(hiddenBalance = hiddenBalance)
    }.cacheState(initialValue = null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val perpsSnapshot: StateFlow<PerpsPortfolioSnapshot?> = multichainWalletId(Perps)
        .flatMapLatest { walletId ->
            if (walletId != null) {
                perpsPortfolioStore.snapshot.map { it?.takeIf { snapshot -> snapshot.walletId == walletId } }
            } else {
                flowOf(null)
            }
        }
        .cacheState()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val perpsShelfMarkets: StateFlow<List<PerpMarket>> = combine(
        perpsSnapshot.map { it != null && !it.loading && it.displayPortfolio()?.positions?.isEmpty() == true }
            .distinctUntilChanged(),
        refreshSignal.onStart { emit(Unit) },
    ) { needed, _ -> needed }
        .filter { it }
        .mapLatest { perpsRepository.getShelfMarkets() }
        .runningFold(emptyList<PerpMarket>()) { previous, loaded -> loaded.ifEmpty { previous } }
        .cacheState(initialValue = emptyList())

    private val perps: StateFlow<WalletSection.Perps?> = combine(
        perpsSnapshot,
        perpsShelfMarkets,
        assetsTotal,
        settingsRepository.hiddenBalancesFlow,
    ) { snapshot, markets, assets, _ ->
        val hiddenBalance = settingsRepository.hiddenBalances
        val portfolio = snapshot?.displayPortfolio()
        val balanceFormat = assets?.takeIf { it.walletId == snapshot?.walletId }?.perpsFormat(hiddenBalance)
        when {
            portfolio == null -> null
            portfolio.positions.isNotEmpty() -> WalletSection.Perps.Positions(
                positions = portfolio.positions.map { it.toUi(hiddenBalance) },
                balanceFormat = balanceFormat,
            )
            markets.isEmpty() -> null
            else -> WalletSection.Perps.Shelf(
                markets = markets,
                balanceFormat = if (snapshot.isAccountMissing) {
                    null
                } else {
                    balanceFormat
                },
            )
        }
    }.cacheState(initialValue = null)

    // Positions win over the carousel: the carousel is what a wallet without any of them sees.
    private val earn: StateFlow<WalletSection.Earn?> = combine(
        multichainWalletId(Earn),
        settingsRepository.hiddenBalancesFlow,
    ) { walletId, _ ->
        if (walletId != null) {
            earnSection(hiddenBalance = settingsRepository.hiddenBalances)
        } else {
            null
        }
    }
        .cacheState(initialValue = null)

    // Open perps positions sit above the assets and the shelf below them; Earn always comes last, as on iOS.
    val sections: StateFlow<List<WalletSection>> = combine(perps, earn) { perps, earn ->
        when (perps) {
            is WalletSection.Perps.Positions -> listOfNotNull(perps, WalletSection.Assets, earn)
            is WalletSection.Perps.Shelf -> listOfNotNull(WalletSection.Assets, perps, earn)
            null -> listOfNotNull(WalletSection.Assets, earn)
        }
    }.cacheState(initialValue = listOf(WalletSection.Assets))

    @OptIn(ExperimentalCoroutinesApi::class)
    val stakingApy: StateFlow<String?> = walletIdRefresh
        .mapLatest { walletId -> loadMaxStakingApy(walletId) }
        .cacheState()

    @OptIn(ExperimentalCoroutinesApi::class)
    val battery: StateFlow<WalletBattery?> = merge(
        selectedWalletId.map { it to false },
        refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
        batteryRepository.balanceUpdatedFlow.mapNotNull { wallet.value?.id?.let { it to false } },
    )
        .mapLatest { (walletId, ignoreCache) -> loadBattery(walletId, ignoreCache) }
        .cacheState()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stakedLoad: StateFlow<StakedLoad> = combine(
        merge(
            selectedWalletId.map { it to false },
            refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
            balanceRefreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
        ),
        settingsRepository.currencyFlow,
        settingsRepository.hiddenBalancesFlow,
    ) { (walletId, ignoreCache), currency, _ ->
        Triple(walletId, currency.code, ignoreCache)
    }
        .transformLatest { (walletId, currencyCode, ignoreCache) ->
            emit(
                StakedLoad(
                    walletId = walletId,
                    currencyCode = currencyCode,
                    items = loadStaked(walletId, ignoreCache),
                )
            )
        }
        .cacheState(initialValue = StakedLoad.Empty)

    val staked: StateFlow<List<StakedUi>> = combine(
        stakedLoad,
        selectedWalletId,
        settingsRepository.currencyFlow,
    ) { load, walletId, currency ->
        if (load.walletId != walletId || load.currencyCode != currency.code) {
            emptyList()
        } else {
            load.items
        }
    }.cacheState(initialValue = emptyList())

    private val total: StateFlow<WalletTotal?> = combine(assetsTotal, stakedLoad) { assets, stakes ->
        if (assets == null) {
            return@combine null
        }
        if (stakes.walletId != assets.walletId || stakes.currencyCode != assets.currencyCode) {
            return@combine assets
        }
        assets.plusNominatorStakes(stakes.items)
    }.cacheState()

    val totalUi: StateFlow<WalletTotalUi?> = combine(total, hiddenBalances) { total, hiddenBalance ->
        when {
            hiddenBalance -> WalletTotalUi.HiddenBalance
            total == null -> null
            else -> WalletTotalUi.Amount(total)
        }
    }.cacheState()

    // The count needs network, so it lives outside the main combine: the card renders right away
    // and the Migration row appears once the count arrives.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val migratableWallets: StateFlow<Int> = combine(
        wallet.filterNotNull(),
        setupRefresh,
    ) { mc, _ -> mc }
        .mapLatest { migratableWalletsCount() }
        .cacheState(initialValue = 0)

    val finishSetup = combine(
        wallet.filterNotNull(),
        backupRepository.stream,
        settingsRepository.walletPrefsChangedFlow,
        setupRefresh,
        total,
    ) { mc, backups, _, _, total ->
        Triple(mc, backups, total)
    }.combine(migratableWallets) { (mc, backups, total), migratableWallets ->
        buildFinishSetupState(mc, backups, total, migratableWallets)
    }.cacheState()

    init {
        bgScope.launch {
            accountRepo.refreshTrigger.collect {
                refreshSignal.tryEmit(Unit)
            }
        }
        bgScope.launch {
            appLifecycle.observeWithoutGrace.drop(1).filter { it }.collect {
                isTotalStale.value = true
                balanceRefreshSignal.tryEmit(Unit)
            }
        }
        bgScope.launch {
            settingsRepository.walletPush.collect {
                pushEnabling.value = false
                setupRefresh.update { it + 1 }
            }
        }
        bgScope.launch {
            settingsRepository.biometricFlow.collect {
                setupRefresh.update { it + 1 }
            }
        }
    }

    fun onEnablePushStarted() {
        pushEnabling.value = true
    }

    fun refreshPushStatus() {
        pushEnabling.value = false
        setupRefresh.update { it + 1 }
    }

    private fun buildFinishSetupState(
        mc: McWalletEntity,
        backups: List<BackupEntity>,
        total: WalletTotal?,
        migratableWallets: Int,
    ): FinishSetupState? {
        val hasBackup = backups.any { it.walletId == mc.id }
        val funded = total != null && total.walletId == mc.id &&
            total.value.toDisplayValue() > BigDecimal.ZERO
        if (funded && !hasBackup) {
            return FinishSetupState(
                lines = listOf(FinishSetupLine.Backup(status = FinishSetupLineStatus.Pending)),
                skippable = false,
            )
        }
        if (settingsRepository.isSetupHidden(mc.id)) {
            return null
        }
        val needsPushSetup = pushStatusProvider.needsPushSetup()
        val lines = buildList {
            if (!hasBackup) {
                add(FinishSetupLine.Backup(status = FinishSetupLineStatus.Pending))
            }
            if (migratableWallets > 0) {
                add(
                    FinishSetupLine.Migration(
                        status = FinishSetupLineStatus.Pending,
                        walletsLeft = migratableWallets,
                    ),
                )
            }
            if (needsPushSetup) {
                add(FinishSetupLine.Push(status = FinishSetupLineStatus.Pending))
            }
            if (!settingsRepository.biometric && biometryStatusProvider.isBiometryAvailable()) {
                add(FinishSetupLine.Biometry(status = FinishSetupLineStatus.Pending))
            }
        }
        if (lines.isEmpty()) {
            return null
        }
        return FinishSetupState(lines = lines, skippable = true)
    }

    private suspend fun migratableWalletsCount(): Int {
        return try {
            migrationStatusProvider.migratableWalletsCount()
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e, "Failed to count migratable wallets")
            0
        }
    }

    fun toggleHiddenBalances() {
        settingsRepository.hiddenBalances = !settingsRepository.hiddenBalances
    }

    fun skipFinishSetup() {
        val id = wallet.value?.id ?: return
        settingsRepository.setupHide(id)
    }

    fun hideBanner(banner: BannerEntity) {
        val walletId = wallet.value?.id ?: return
        bgScope.launch {
            bannerRepository.hideBanner(walletId, banner.id)
        }
    }

    fun refreshAssets() {
        refreshSignal.tryEmit(Unit)
        setupRefresh.update { it + 1 }
        perpsPortfolioStore.refresh()
    }

    fun refreshPerps() {
        perpsPortfolioStore.refresh()
        refreshWalletTotals()
    }

    private fun refreshWalletTotals() {
        val loaded = assetsTotal.value ?: return
        if (!Perps.isEnabled) {
            return
        }
        totalsRefresh?.cancel()
        totalsRefresh = bgScope.launch {
            val totals = try {
                accountRepo.fetchWalletTotals(loaded.walletId, loaded.currencyCode)
            } catch (e: Throwable) {
                verifyError(e)
                L.e(e, "Failed to refresh wallet totals")
                return@launch
            }
            assetsTotal.update { current ->
                val sameScope = current != null &&
                    current.walletId == loaded.walletId &&
                    current.currencyCode == loaded.currencyCode
                if (sameScope) {
                    current.copy(
                        value = DisplayUnit(totals.total, Decimal(2)),
                        perps = totals.perpsTotal.resolve(current.perps),
                    )
                } else {
                    current
                }
            }
        }
    }

    fun setResumed(resumed: Boolean) {
        perpsLive.set(resumed)
    }

    fun onEarnPositionsShown() {
        bgScope.launch {
            val portfolio = earnRepository.portfolio()
            EarnAnalytics.yourVaultsView(portfolio.positions.size, portfolio.totalUsd.toDouble())
        }
    }

    fun onEarnVaultsShown() {
        bgScope.launch {
            EarnAnalytics.featuredView(featuredVaults().size)
        }
    }

    override fun onCleared() {
        perpsLive.close()
        super.onCleared()
    }

    // Earn and Perps live only in multichain wallets: McAccountRepository has no row for a legacy one.
    private fun multichainWalletId(feature: Features<Boolean>): Flow<String?> = combine(
        wallet,
        refreshSignal.onStart { emit(Unit) },
    ) { wallet, _ -> wallet?.id?.takeIf { feature.isEnabled } }
        .distinctUntilChanged()

    private fun earnSection(hiddenBalance: Boolean): WalletSection.Earn? {
        val portfolio = earnRepository.portfolio()
        val positions = portfolio.positions.map { EarnUiMapper.mapPosition(it, hiddenBalance) }
        if (positions.isNotEmpty()) {
            return WalletSection.Earn.Positions(
                positions = positions,
                totalFormat = EarnUiMapper.formatTotal(portfolio.totalUsd, hiddenBalance),
            )
        }
        val vaults = featuredVaults()
        return if (vaults.isEmpty()) {
            null
        } else {
            WalletSection.Earn.Vaults(vaults)
        }
    }

    private fun featuredVaults(): List<EarnVaultUi> {
        return earnRepository.featuredVaults()
            .map(EarnUiMapper::mapVault)
            // The carousel keys its cards, and a repeated vault would crash the whole screen.
            .distinctBy { it.id }
    }

    private fun handleRefreshError(error: Throwable) {
        errorRelay.emit(error)
    }

    private fun confirmTotal(walletId: String, currencyCode: String) {
        val total = assetsTotal.value ?: return
        if (total.walletId == walletId && total.currencyCode == currencyCode) {
            isTotalStale.value = false
        }
    }

    private suspend fun loadMaxStakingApy(walletId: String): String? {
        val tonWallet = withContext(Dispatchers.IO) {
            unifiedAccountRepository.getTonWalletById(walletId)
        } ?: return null

        if (tonWallet.testnet) {
            return null
        }

        return stakingRepository.getMaxApyFormatted(
            accountId = tonWallet.accountId,
            network = tonWallet.network,
            initializedAccount = tonWallet.initialized,
        )
    }

    private suspend fun loadBattery(
        walletId: String,
        ignoreCache: Boolean = false,
    ): WalletBattery? = withContext(Dispatchers.IO) {
        val tonWallet = unifiedAccountRepository.getTonWalletById(walletId) ?: return@withContext null
        if (tonWallet.testnet) {
            return@withContext null
        }

        val balance = batteryRepository.getBalance(tonWallet, ignoreCache).balance

        if (api.getConfig(tonWallet.network).flags.disableBattery && !balance.isPositive) {
            return@withContext null
        }

        WalletBattery(
            level = BatteryMapper.convertToBatteryLevel(balance),
            viewed = settingsRepository.batteryViewed,
            negative = balance.isNegative,
        )
    }

    private suspend fun loadTonAddress(walletId: String): String? {
        return try {
            accountRepo.getTonAccount(walletId)?.displayAddress
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e, "Failed to load TON address")
            null
        }
    }

    private suspend fun loadStaked(
        walletId: String,
        ignoreCache: Boolean = false,
    ): List<StakedUi> = withContext(Dispatchers.IO) {
        val tonWallet = unifiedAccountRepository.getTonWalletById(walletId) ?: return@withContext emptyList()
        if (tonWallet.testnet) {
            return@withContext emptyList()
        }
        StakingAssetsBridge.loadRows(
            walletId = walletId,
            accountId = tonWallet.accountId,
            network = tonWallet.network,
            initializedAccount = tonWallet.initialized,
            currencyCode = settingsRepository.currency.code,
            hiddenBalance = settingsRepository.hiddenBalances,
            accountRepo = accountRepo,
            stakingRepository = stakingRepository,
            ignoreCache = ignoreCache,
        )
    }

    private fun collectiblesFlow(
        walletId: String,
        isOnline: Boolean,
    ): Flow<WalletCollectiblesState?> = flow {
        emit(null)
        val tonWallet = withContext(Dispatchers.IO) {
            unifiedAccountRepository.getTonWalletById(walletId)
        } ?: return@flow
        if (api.getConfig(tonWallet.network).flags.disableNfts) {
            return@flow
        }
        emitAll(
            flow {
                collectiblesRepository.getFlow(tonWallet.address, tonWallet.network, isOnline).collect { result ->
                    emit(mapCollectiblesState(wallet = tonWallet, result = result))
                }
            },
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun mapCollectiblesState(
        wallet: WalletEntity,
        result: com.tonapps.wallet.data.collectibles.entities.NftListResult,
    ): WalletCollectiblesState? {
        if (result.list.isEmpty()) {
            return null
        }
        val safeMode = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network)
        var hasUserHidden = false
        val items = buildList {
            for (nft in result.list) {
                if (safeMode && !nft.verified) {
                    continue
                }
                val nftPref = settingsRepository.getTokenPrefs(wallet.id, nft.collectionAddressOrNFTAddress)
                if (nftPref.isHidden) {
                    hasUserHidden = true
                    continue
                }
                add(applyNftPref(nft, nftPref))
            }
        }
        return when {
            items.isNotEmpty() -> WalletCollectiblesState(
                items = items,
                hiddenBalance = hiddenBalances.value,
            )
            hasUserHidden -> WalletCollectiblesState(
                items = emptyList(),
                allHidden = true,
                hiddenBalance = hiddenBalances.value,
            )
            else -> null
        }
    }

    private fun applyNftPref(nft: NftEntity, pref: TokenPrefsEntity): NftEntity {
        if (pref.isTrust) {
            return nft.copy(trust = Trust.whitelist)
        }
        return nft
    }
}

private data class StakedLoad(
    val walletId: String,
    val currencyCode: String,
    val items: List<StakedUi>,
) {
    companion object {
        val Empty = StakedLoad(walletId = "", currencyCode = "", items = emptyList())
    }
}

private fun WalletTotal?.perpsOf(walletId: String, currencyCode: String): BigDecimal? =
    this?.takeIf { it.walletId == walletId && it.currencyCode == currencyCode }?.perps

private fun WalletPerpsTotal.resolve(previous: BigDecimal?): BigDecimal? = when (this) {
    WalletPerpsTotal.Unavailable -> previous
    WalletPerpsTotal.Empty -> null
    is WalletPerpsTotal.Amount -> value
}

private fun WalletTotal.perpsFormat(hiddenBalance: Boolean): String? {
    val perps = perps ?: return null
    if (hiddenBalance) {
        return HIDDEN_BALANCE
    }
    return formatFiatAmount(JavaBigDecimal(perps.toStringExpanded()), currencyCode)
}

private fun WalletTotal.plusNominatorStakes(stakes: List<StakedUi>): WalletTotal {
    val extra = stakes
        .asSequence()
        .filter { it.liquidAssetId == null }
        .fold(BigDecimal.ZERO) { acc, stake -> acc + stake.fiat }
    if (extra <= BigDecimal.ZERO) {
        return this
    }
    return copy(value = DisplayUnit(value.toDisplayValue() + extra, Decimal(2)))
}