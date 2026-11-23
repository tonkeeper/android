package com.tonapps.portfolio.screens.wallet

import com.tonapps.chainkit.core.chain.model.account.Chain
import kotlinx.coroutines.CancellationException
import com.tonapps.wallet.data.ledger.LedgerWalletInteractor
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map as mapPaging
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.screenrender.ScreenRenderErrorKind
import com.tonapps.bus.screenrender.ScreenRenderErrors
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.bus.screenrender.ScreenRenderStage
import com.tonapps.bus.screenrender.ScreenRenderTracker
import com.tonapps.chainkit.core.chain.model.num.CoinValue
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.core.flags.EarnTooltipInteractor
import com.tonapps.core.flags.Features
import com.tonapps.core.flags.WalletFeature.Earn
import com.tonapps.core.flags.WalletFeature.MoonPayRamp
import com.tonapps.core.flags.WalletFeature.Perps
import com.tonapps.core.helper.EarnAnalytics
import com.tonapps.core.loyalty.LOYALTY_BANNER_ID
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
import com.tonapps.perps.data.sortedByLiquidationProximity
import com.tonapps.perps.screens.portfolio.toUi
import com.tonapps.portfolio.wallet.WalletLimitedSection
import com.tonapps.portfolio.wallet.WalletSectionLimit
import com.tonapps.portfolio.wallet.WalletSectionLimitStore
import com.tonapps.wallet.data.earn.EarnRepository
import com.tonapps.wallet.data.earn.entities.EarnPortfolioState
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
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.core.Trust
import com.tonapps.wallet.data.loyalty.LoyaltyEntry
import com.tonapps.wallet.data.loyalty.LoyaltyRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.WalletAssetsQuery
import com.tonapps.core.components.isNativeTon
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.WALLET_ASSETS_PAGE_SIZE
import com.tonapps.wallet.data.multichain.account.WalletPerpsTotal
import com.tonapps.wallet.data.multichain.account.walletAssetsFilters
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
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
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal as JavaBigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean

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
    val walletId: String,
    val level: Float,
    val viewed: Boolean,
    val negative: Boolean,
    val knownEmpty: Boolean,
)

enum class WalletAction {
    Send, Deposit, Buy, Swap, Stake
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
    private val loyaltyRepository: LoyaltyRepository,
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
    private val ledgerWallets: LedgerWalletInteractor,
    private val earnTooltipInteractor: EarnTooltipInteractor,
    private val passcodeManager: PasscodeManager,
    private val sectionLimits: WalletSectionLimitStore,
) : AsyncViewModel() {

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var refreshRelayed = false
    private val earnRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val earnPullPending = AtomicBoolean(false)
    private val balanceRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val accountsPagingCacheSession = AccountsPagingCacheSession()
    private val perpsLive = perpsPortfolioStore.liveSubscription()
    private val resumed = MutableStateFlow(false)
    private val homeTooltips = WalletHomeTooltips(earnTooltipInteractor, settingsRepository)

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
        mcWallet != null && !mcWallet.isHardware && backups.none { it.walletId == mcWallet.id }
    }.cacheState(initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val tonAddress: StateFlow<String?> = wallet
        .mapNotNull { it?.id }
        .distinctUntilChanged()
        .mapLatest { walletId -> loadTonAddress(walletId) }
        .cacheState()

    val pushEnabling: StateFlow<Boolean> field = MutableStateFlow(false)

    val hiddenBalances: StateFlow<Boolean> = settingsRepository.hiddenBalancesFlow
        .map { settingsRepository.hiddenBalances }
        .cacheState(initialValue = settingsRepository.hiddenBalances)

    private val assetsTotal = MutableStateFlow<WalletTotal?>(null)
    private val screenRender = ScreenRenderTracker.shared

    @Volatile
    private var assetsErrorKind: ScreenRenderErrorKind? = null
    private var hasAppeared = false
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

    // McAccountRepository.refreshTrigger replays a refresh to every new collector, and init relays it after the blocks
    // subscribe. So the first selection only reads the cache, and that refresh goes to the network. If the refresh was
    // relayed first, it may be lost, and the first selection loads "cache, else network". A later selection has no
    // refresh after it.
    private val selectionLoads = selectedWalletId.withIndex().map { (index, walletId) ->
        val mode = if (index == 0 && !refreshRelayed) {
            LoadMode.CacheOnly
        } else {
            LoadMode.CacheFirst
        }
        walletId to mode
    }

    private val setupRefresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val raffles: StateFlow<List<RaffleEntity>> = merge(
        selectedWalletId.map { it to LoadMode.CacheOnly },
        // The first refresh comes at launch, while RootViewModel prefetches the selected wallet's raffles. That
        // prefetch stands in for the cache: the refresh loads only if it fails.
        refreshSignal.withIndex().mapNotNull { (index, _) ->
            val mode = if (index == 0) {
                LoadMode.CacheFirst
            } else {
                LoadMode.Network
            }
            accountRepoLegacy.getSelectedWalletId()?.let { it to mode }
        },
        onChainEvents.mapNotNull { accountRepoLegacy.getSelectedWalletId() }.map { it to LoadMode.Network },
    )
        .flatMapLatestCatching { (walletId, mode) ->
            when (mode) {
                LoadMode.CacheOnly -> raffleRepository.getRafflesFlow(walletId, forced = false)
                LoadMode.CacheFirst -> raffleRepository.getRafflesFlowAfterPrefetch(walletId)
                LoadMode.Network -> raffleRepository.getRafflesFlow(walletId, forced = true)
            }
        }
        .cacheState(initialValue = emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val raffleRows: StateFlow<List<RaffleEntity>> = raffles
        .transformLatest { raffles ->
            while (true) {
                val now = OffsetDateTime.now()
                emit(raffles.filter { it.hasStarted(now) && !it.isOfferPhase && !it.hasLeaderboard })
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
    val banners: StateFlow<List<BannerEntity>> = merge(
        selectionLoads.map { (walletId, mode) -> walletId to (mode != LoadMode.CacheOnly) },
        refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
        onChainEvents.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
    )
        .flatMapLatest { (walletId, remote) ->
            if (remote) {
                bannerRepository.getBannersFlow(walletId, isMultichain = true)
            } else {
                flow { emit(bannerRepository.getBanners(walletId, isMultichain = true)) }
            }
        }
        .cacheState(initialValue = emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val loyalty: StateFlow<LoyaltyEntry> = combine(
        wallet.map { it?.id }.distinctUntilChanged(),
        merge(refreshSignal, resumed.filter { it }.map { }).onStart { emit(Unit) },
    ) { walletId, _ -> walletId }
        .flatMapLatest { walletId ->
            walletId?.let(loyaltyRepository::entryFlow) ?: flowOf(LoyaltyEntry.Hidden)
        }
        .cacheState(initialValue = LoyaltyEntry.Hidden)

    // The flag is not reactive: a remote config that lands while the screen is shown takes effect on the next resume.
    val actions: StateFlow<List<WalletAction>> = resumed
        .filter { it }
        .map { walletActions() }
        .cacheState(initialValue = walletActions())

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
            val assetsQuery = walletAssetsQuery(walletId, currencyCode)
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
                        walletId = assetsQuery.walletId,
                        currency = assetsQuery.currency,
                        query = assetsQuery.query,
                        network = assetsQuery.network,
                        availableOnly = assetsQuery.availableOnly,
                        showHidden = assetsQuery.showHidden,
                        showAll = assetsQuery.showAll,
                        capabilities = assetsQuery.capabilities,
                        verifiedOnly = assetsQuery.verifiedOnly,
                        hideDust = assetsQuery.hideDust,
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
                        onNetworkConfirmed = {
                            confirmTotal(walletId, currencyCode)
                            assetsErrorKind = null
                            screenRender.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Portfolio)
                        },
                        onLoadError = { error -> handleRefreshError(error) },
                        onRefreshError = { error ->
                            val kind = ScreenRenderErrors.kind(error)
                            assetsErrorKind = kind
                            screenRender.failed(ScreenRenderId.Portfolio, kind)
                        },
                        onCachedLoaded = { screenRender.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.Portfolio) },
                        onRequestStarted = { screenRender.requestStarted(ScreenRenderId.Portfolio) },
                        onRequestFinished = { screenRender.requestFinished(ScreenRenderId.Portfolio) },
                    )
                },
            ).flow.map { pagingData ->
                if (assetsQuery.verifiedOnly) {
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
    private val perpsSnapshot: StateFlow<PerpsPortfolioSnapshot?> = multichainWalletId(Perps, allowHardware = false)
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
                positions = portfolio.positions.sortedByLiquidationProximity().map { it.toUi(hiddenBalance) },
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val earnPortfolio: StateFlow<Pair<String, EarnPortfolioState?>?> = combine(
        multichainWalletId(Earn),
        settingsRepository.currencyFlow,
    ) { walletId, currency -> walletId?.let { it to currency.code } }
        .distinctUntilChanged()
        .flatMapLatest { request ->
            if (request != null) {
                val (walletId, currency) = request
                merge(refreshSignal, earnRefreshSignal)
                    .map { true }
                    .onStart { emit(true) }
                    .flatMapLatest { forced ->
                        earnRepository.getPortfolioFlow(walletId, currency, forced) { earnPullPending.getAndSet(false) }
                    }
                    .map { walletId to it }
            } else {
                flowOf(null)
            }
        }
        .cacheState(initialValue = null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val earnVaults: StateFlow<List<EarnVaultUi>?> = multichainWalletId(Earn)
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) {
                merge(refreshSignal.map { true }, earnRefreshSignal.map { false })
                    .onStart { emit(true) }
                    .flatMapLatest { forced -> earnRepository.getFeaturedVaultsFlow(forced) }
            } else {
                flowOf(null)
            }
        }
        // The carousel keys its cards, and a repeated vault would crash the whole screen.
        .map { vaults -> vaults?.map(EarnUiMapper::mapVault)?.distinctBy { it.id } }
        .cacheState(initialValue = null)

    // Positions win over the carousel: the carousel is what a wallet without any of them sees.
    private val earn: StateFlow<WalletSection.Earn?> = combine(
        multichainWalletId(Earn),
        earnPortfolio,
        earnVaults,
        settingsRepository.hiddenBalancesFlow,
        sectionLimits.limit(WalletLimitedSection.Earn),
    ) { walletId, portfolio, vaults, _, limit ->
        val state = portfolio?.takeIf { it.first == walletId }?.second
        earnSection(state, vaults, hiddenBalance = settingsRepository.hiddenBalances, limit = limit)
    }
        .cacheState(initialValue = null)

    // As on iOS: open perps positions sit above the assets and the shelf below them;
    // Earn positions come before the shelf, the Earn carousel after it.
    val sections: StateFlow<List<WalletSection>> = combine(perps, earn) { perps, earn ->
        when (perps) {
            is WalletSection.Perps.Positions -> listOfNotNull(perps, WalletSection.Assets, earn)
            is WalletSection.Perps.Shelf -> when (earn) {
                is WalletSection.Earn.Vaults, null -> listOfNotNull(WalletSection.Assets, perps, earn)
                is WalletSection.Earn.Positions, is WalletSection.Earn.Unavailable ->
                    listOf(WalletSection.Assets, earn, perps)
            }
            null -> listOfNotNull(WalletSection.Assets, earn)
        }
    }.cacheState(initialValue = listOf(WalletSection.Assets))

    val assetsLimit: StateFlow<WalletSectionLimit> = sectionLimits.limit(WalletLimitedSection.Assets)

    // The lockscreen is an overlay in the activity, so the screen is resumed and on top under it.
    // Only a wallet without positions sees it: it points at the carousel of vaults.
    private val homeTooltip: StateFlow<HomeTooltip?> = homeTooltips.active(
        onScreen = combine(resumed, passcodeManager.lockscreenHiddenFlow) { resumed, unlocked -> resumed && unlocked },
        earn = earn,
    ).cacheState(initialValue = null)

    val showsEarnTooltip: StateFlow<Boolean> = homeTooltip.map { it == HomeTooltip.Earn }.cacheState(initialValue = false)

    val showsPerpsTooltip: StateFlow<Boolean> = homeTooltip.map { it == HomeTooltip.Perps }.cacheState(initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cachedAccounts: StateFlow<List<AccountWithDetails>> = combine(
        selectedWalletId,
        settingsRepository.currencyFlow,
        settingsRepository.safeModeChangedFlow,
        settingsRepository.hideDustAssetsFlow,
    ) { walletId, walletCurrency, _, _ ->
        walletId to walletCurrency.code
    }
        .flatMapLatest { (walletId, currencyCode) ->
            accountRepo.observeCachedAccounts(walletAssetsQuery(walletId, currencyCode))
        }
        .cacheState(initialValue = emptyList())

    private val earnOffersFailed = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val earnAssetApy: StateFlow<Map<String, String>> = combine(
        multichainWalletId(Earn),
        cachedAccounts.map { accounts -> accounts.map { it.asset.id } }.distinctUntilChanged(),
        refreshSignal.filter { earnOffersFailed.value }.onStart { emit(Unit) },
    ) { walletId, assetIds, _ -> assetIds.takeIf { walletId != null } }
        .transformLatest { assetIds ->
            if (assetIds.isNullOrEmpty()) {
                emit(emptyMap())
                return@transformLatest
            }
            val offers = try {
                earnRepository.loadAssetOffers(assetIds)
            } catch (e: Throwable) {
                verifyError(e)
                L.w(e, "Earn offers failed")
                earnOffersFailed.value = true
                return@transformLatest
            }
            earnOffersFailed.value = false
            emit(offers.mapValues { (_, offer) -> CurrencyFormatter.formatPercent(offer.apy).toString() })
        }
        .cacheState(initialValue = emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val battery: StateFlow<WalletBattery?> = merge(
        selectionLoads,
        refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to LoadMode.Network } },
        selectedWalletId.distinctUntilChanged().flatMapLatest { walletId ->
            batteryRepository.balanceUpdatedFlow.drop(1).map { walletId to LoadMode.CacheFirst }
        },
    )
        .mapLatest { (walletId, mode) -> loadBattery(walletId, mode) }
        .cacheState()

    val batteryHint: StateFlow<Boolean> = combine(
        resumed,
        passcodeManager.lockscreenHiddenFlow,
        selectedWalletId,
        battery,
        settingsRepository.batteryHintFlow,
    ) { resumed, unlocked, walletId, battery, available ->
        resumed && unlocked && available && battery?.knownEmpty == true && battery.walletId == walletId
    }.cacheState(initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stakedLoad: StateFlow<StakedLoad> = combine(
        merge(
            selectionLoads,
            refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to LoadMode.Network } },
            balanceRefreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to LoadMode.Network } },
        ),
        settingsRepository.currencyFlow,
        settingsRepository.hiddenBalancesFlow,
    ) { (walletId, mode), currency, _ ->
        Triple(walletId, currency.code, mode)
    }
        .transformLatest { (walletId, currencyCode, mode) ->
            emit(
                StakedLoad(
                    walletId = walletId,
                    currencyCode = currencyCode,
                    items = loadStaked(walletId, mode),
                    // Read from the staking cache that loadStaked has just filled: no request of its own.
                    maxApy = loadMaxStakingApy(walletId),
                )
            )
        }
        .cacheState(initialValue = StakedLoad.Empty)

    val assetApy: StateFlow<Map<String, String>> = combine(
        cachedAccounts,
        combine(stakedLoad, selectedWalletId) { load, walletId ->
            load.maxApy.takeIf { load.walletId == walletId }
        }.distinctUntilChanged(),
        earnAssetApy,
    ) { accounts, staking, earn ->
        accounts.mapNotNull { account ->
            val apy = if (account.asset.isNativeTon()) {
                staking
            } else {
                earn[account.asset.id.lowercase()]
            }
            apy?.let { account.asset.id to it }
        }.toMap()
    }.cacheState(initialValue = emptyMap())

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ledgerMissingChains: StateFlow<List<Chain>> = combine(
        wallet.filterNotNull(),
        setupRefresh,
    ) { mc, _ -> mc }
        .mapLatest { mc -> if (mc.isHardware) { ledgerMissingChains(mc.id) } else { emptyList() } }
        .cacheState(initialValue = emptyList())

    val finishSetup = combine(
        wallet.filterNotNull(),
        backupRepository.stream,
        settingsRepository.walletPrefsChangedFlow,
        setupRefresh,
        total,
    ) { mc, backups, _, _, total ->
        Triple(mc, backups, total)
    }.combine(combine(migratableWallets, ledgerMissingChains, ::Pair)) { (mc, backups, total), (migratableWallets, ledgerChains) ->
        buildFinishSetupState(mc, backups, total, migratableWallets, ledgerChains)
    }.cacheState()

    init {
        bgScope.launch {
            homeTooltips.load()
        }
        bgScope.launch {
            accountRepo.refreshTrigger.collect {
                refreshRelayed = true
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
        ledgerChains: List<Chain>,
    ): FinishSetupState? {
        val hasBackup = mc.isHardware || backups.any { it.walletId == mc.id }
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
            if (ledgerChains.isNotEmpty()) {
                add(FinishSetupLine.LedgerNetworks(status = FinishSetupLineStatus.Pending, chains = ledgerChains))
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

    private suspend fun ledgerMissingChains(walletId: String): List<Chain> = try {
        ledgerWallets.missingChains(walletId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        L.e(e, "Failed to read the networks missing from the Ledger wallet")
        LedgerNonFatals.recordIssue(e, LedgerIssueSite.Wallet)
        emptyList()
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
            if (banner.id == LOYALTY_BANNER_ID) {
                loyaltyRepository.hideBanner(walletId)
            } else {
                bannerRepository.hideBanner(walletId, banner.id)
            }
        }
    }

    fun refreshAssets() {
        if (Earn.isEnabled && wallet.value != null) {
            earnPullPending.set(true)
        }
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
        val earnShown = showsEarnTooltip.value
        if (resumed) {
            homeTooltips.recheckPerps()
        }
        this.resumed.value = resumed
        if (!resumed && earnShown) {
            dismissEarnTooltip()
        }
        perpsLive.set(resumed)
        if (resumed) {
            earnRefreshSignal.tryEmit(Unit)
        }
    }

    fun dismissEarnTooltip() = homeTooltips.dismissEarn()

    fun setPerpsShelfVisibility(visibility: PerpsShelfVisibility) = homeTooltips.setPerpsShelf(visibility)

    fun dismissPerpsTooltip() = homeTooltips.dismissPerps()

    fun retryEarn() {
        earnRefreshSignal.tryEmit(Unit)
    }

    fun selectAssetsLimit(limit: WalletSectionLimit) {
        sectionLimits.setLimit(WalletLimitedSection.Assets, limit)
    }

    fun selectEarnLimit(limit: WalletSectionLimit) {
        sectionLimits.setLimit(WalletLimitedSection.Earn, limit)
    }

    fun onEarnPositionsShown() {
        val walletId = wallet.value?.id ?: return
        val state = earnPortfolio.value?.takeIf { it.first == walletId }?.second as? EarnPortfolioState.Loaded ?: return
        val portfolio = state.portfolio
        EarnAnalytics.yourVaultsView(portfolio.positions.size, portfolio.totalUsd.toDouble())
    }

    fun onEarnVaultsShown() {
        val vaults = earnVaults.value ?: return
        EarnAnalytics.featuredView(vaults.size)
    }

    override fun onCleared() {
        perpsLive.close()
        super.onCleared()
    }

    // Earn and Perps live only in multichain wallets: McAccountRepository has no row for a legacy one.
    private fun multichainWalletId(
        feature: Features<Boolean>,
        allowHardware: Boolean = true,
    ): Flow<String?> = combine(
        wallet,
        refreshSignal.onStart { emit(Unit) },
    ) { wallet, _ -> wallet?.takeIf { allowHardware || !it.isHardware }?.id?.takeIf { feature.isEnabled } }
        .distinctUntilChanged()

    private fun walletAssetsQuery(walletId: String, currencyCode: String): WalletAssetsQuery {
        val filters = settingsRepository.walletAssetsFilters(walletId)
        return WalletAssetsQuery(
            walletId = walletId,
            currency = currencyCode,
            verifiedOnly = filters.verifiedOnly,
            hideDust = filters.hideDust,
        )
    }

    private fun earnSection(
        state: EarnPortfolioState?,
        vaults: List<EarnVaultUi>?,
        hiddenBalance: Boolean,
        limit: WalletSectionLimit,
    ): WalletSection.Earn? {
        val loaded = when (state) {
            null -> return null
            is EarnPortfolioState.Unavailable -> return WalletSection.Earn.Unavailable
            is EarnPortfolioState.Loaded -> state
        }
        val portfolio = loaded.portfolio
        if (portfolio.positions.isNotEmpty()) {
            val positions = portfolio.positions.map { EarnUiMapper.mapPosition(it, portfolio.currency, hiddenBalance) }
            return WalletSection.Earn.Positions(
                positions = positions.take(limit.count),
                hiddenPositions = positions.drop(limit.count),
                limit = limit,
                totalFormat = EarnUiMapper.formatFiat(portfolio.currency, portfolio.totalFiat, hiddenBalance),
                isStale = loaded.isStale,
            )
        }
        return if (vaults.isNullOrEmpty()) {
            null
        } else {
            WalletSection.Earn.Vaults(vaults)
        }
    }

    /**
     * The tab is shown again. Assets are not reloaded on that, so the visit ends with what is on screen,
     * or with the last load's failure.
     */
    fun onScreenShown() {
        if (!hasAppeared) {
            hasAppeared = true
            return
        }
        val errorKind = assetsErrorKind
        if (assetsTotal.value == null && errorKind == null) {
            // Still loading: the session ends with that load.
            return
        }
        if (assetsTotal.value != null) {
            screenRender.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.Portfolio)
        }
        if (errorKind != null) {
            screenRender.failed(ScreenRenderId.Portfolio, errorKind)
        } else {
            screenRender.finish(ScreenRenderId.Portfolio)
        }
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

        return stakingRepository.getCachedMaxApyFormatted(
            accountId = tonWallet.accountId,
            network = tonWallet.network,
        )
    }

    private suspend fun loadBattery(
        walletId: String,
        mode: LoadMode,
    ): WalletBattery? = withContext(Dispatchers.IO) {
        val tonWallet = unifiedAccountRepository.getTonWalletById(walletId) ?: return@withContext null
        if (tonWallet.testnet) {
            return@withContext null
        }

        val entity = if (mode == LoadMode.CacheOnly) {
            batteryRepository.getCachedBalance(tonWallet) ?: return@withContext null
        } else {
            batteryRepository.getBalance(tonWallet, ignoreCache = mode == LoadMode.Network)
        }
        val balance = entity.balance

        if (api.getConfig(tonWallet.network).flags.disableBattery && !balance.isPositive) {
            return@withContext null
        }

        WalletBattery(
            walletId = walletId,
            level = BatteryMapper.convertToBatteryLevel(balance),
            viewed = settingsRepository.batteryViewed,
            negative = balance.isNegative,
            knownEmpty = entity !== BatteryBalanceEntity.Empty && balance.value.signum() == 0,
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
        mode: LoadMode,
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
            mode = mode,
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

internal enum class LoadMode { CacheOnly, CacheFirst, Network }

private data class StakedLoad(
    val walletId: String,
    val currencyCode: String,
    val items: List<StakedUi>,
    val maxApy: String? = null,
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

// The flag opens the buying screen right away (DepositMulticoinRouter), so the button says so, as on iOS.
private fun walletActions(): List<WalletAction> {
    val deposit = when {
        MoonPayRamp.isEnabled -> WalletAction.Buy
        else -> WalletAction.Deposit
    }
    return listOf(WalletAction.Send, deposit, WalletAction.Swap, WalletAction.Stake)
}
