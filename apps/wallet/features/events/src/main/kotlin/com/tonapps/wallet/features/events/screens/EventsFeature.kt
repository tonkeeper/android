package com.tonapps.wallet.features.events.screens

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.bus.screenrender.ScreenRenderErrors
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.bus.screenrender.ScreenRenderLifecycle
import com.tonapps.bus.screenrender.ScreenRenderStage
import com.tonapps.bus.screenrender.ScreenRenderTracker
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.flags.WalletFeature
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.network.NetworkMonitor
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsRealtime
import com.tonapps.perps.data.PerpsRealtimeEvent
import com.tonapps.perps.funding.PerpsDepositStatus
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.funding.PerpsFundingRecovery
import com.tonapps.perps.funding.perpsTransactionHash
import com.tonapps.perps.funding.toDepositStatus
import com.tonapps.perps.screens.depositstatus.PerpsDepositStatusUi
import com.tonapps.perps.screens.depositstatus.toStatusUi
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.toApiChain
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.HistoryNft
import com.tonapps.wallet.features.events.data.HistoryNftResolver
import com.tonapps.wallet.features.events.data.HistoryPerpsMarketResolver
import com.tonapps.wallet.features.events.data.McEventsRepository
import com.tonapps.wallet.features.events.data.OpenedHistoryEvent
import com.tonapps.wallet.features.events.data.PerpsBalanceChangeStatus
import com.tonapps.wallet.features.events.data.PerpsDepositOrigin
import com.tonapps.wallet.features.events.data.PerpsDepositOriginRecord
import com.tonapps.wallet.features.events.data.PerpsEventKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private const val PENDING_REFRESH_INTERVAL_MS = 10_000L
private const val PENDING_REFRESH_SLOW_INTERVAL_MS = 60_000L
private const val PENDING_REFRESH_FAST_TICKS = 6

private data class EventsQuery(
    val walletId: String,
    val chain: Chain?,
    val assetId: String?,
    val typeFilter: EventsTypeFilter,
    val tonNetwork: TonNetwork,
    val hideDust: Boolean,
    val showPerps: Boolean,
    val hiddenBalance: Boolean,
)

private data class ActivitySelection(
    val opened: OpenedHistoryEvent,
    val shown: WalletActivityListItem.Entry,
)

internal data class EventsTypeSelectorState(
    val items: List<String>,
    val selectedIndex: Int,
    val selected: EventsTypeFilter,
    val entries: List<EventsTypeFilter>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EventsFeature(
    private val application: Application,
    private val eventsRepository: McEventsRepository,
    private val nftResolver: HistoryNftResolver,
    private val perpsMarketResolver: HistoryPerpsMarketResolver,
    private val accountRepoLegacy: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
    private val realtimeProvider: McWalletRealtimeProvider,
    unifiedAccountRepository: UnifiedAccountRepository,
    private val fundingRecovery: PerpsFundingRecovery,
    perpsRealtime: PerpsRealtime,
    private val assetId: String? = null,
    private val mode: EventsScreenMode = EventsScreenMode.General,
    awaitsFundingActivity: Boolean = false,
) : AsyncViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnlineFlow
        .cacheState(initialValue = true)

    // Asset and perps histories use the same screen; only the wallet history reports its render timing.
    private val reportsScreenRender = mode == EventsScreenMode.General && assetId == null
    private val screenRender = ScreenRenderTracker.shared

    init {
        if (reportsScreenRender) {
            screenRender.lifecycle(ScreenRenderLifecycle.Created, ScreenRenderId.History, this)
        }
        eventsRepository.invalidateFirstPageCache()
        bgScope.launch {
            accountRepo.refreshTrigger.collect { eventsRepository.invalidateFirstPageCache() }
        }
    }

    val wallet = combine(
        accountRepoLegacy.selectedWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        accountRepo.refreshTrigger,
    ) { selected, _, _ ->
        accountRepo.getWallet(selected.id)
    }.cacheState()

    val chainFilter: StateFlow<Chain?> field =
        MutableStateFlow(assetId?.let { Asset.coinFromString(it)?.chain })
    private val typeFilter = MutableStateFlow(
        if (mode == EventsScreenMode.Perps) {
            EventsTypeFilter.Perps
        } else {
            EventsTypeFilter.All
        },
    )

    private val perpsAllowed: StateFlow<Boolean> = if (mode == EventsScreenMode.Perps) {
        MutableStateFlow(true)
    } else {
        unifiedAccountRepository.selectedTonWalletFlow
            .flowMap { wallet ->
                assetId == null && wallet?.supportsPerps == true && WalletFeature.Perps.isEnabled
            }
            .cacheState(initialValue = false)
    }

    init {
        bgScope.launch {
            perpsAllowed.collect { allowed ->
                if (!allowed && typeFilter.value == EventsTypeFilter.Perps) {
                    typeFilter.value = EventsTypeFilter.All
                }
            }
        }
    }

    // null = unknown/loading. Means "has a non-dust activity, or has spam" once the dust
    // filter is on, since the initial fetch is filtered too; consumed only for the All filter.
    internal val hasAnyActivity: StateFlow<Boolean?> field = MutableStateFlow(null)

    private val hasPendingActivities = MutableStateFlow(false)

    private val pendingActivitiesRefreshSignal: Flow<Unit> =
        combine(
            hasPendingActivities,
            isOnline,
            wallet,
            realtimeProvider.subscribedWalletId,
        ) { hasPending, online, wallet, subscribedWalletId ->
            hasPending && online && (wallet == null || wallet.id != subscribedWalletId)
        }
            .distinctUntilChanged()
            .flatMapLatest { shouldRefresh ->
                if (shouldRefresh) {
                    pendingActivitiesTicker()
                } else {
                    emptyFlow()
                }
            }

    private val realtimeRefreshSignal: Flow<Unit> = realtimeProvider.activityHints
        .filter { walletId -> walletId == wallet.value?.id }
        .flowMap { }

    private val perpsFundingRefreshSignal: Flow<Unit> = perpsRealtime.events
        .filter { event ->
            (event is PerpsRealtimeEvent.Deposit || event is PerpsRealtimeEvent.Withdrawal) &&
                event.walletId == wallet.value?.id
        }
        .flowMap { }

    private val fundingRefreshMode: Flow<FundingRefreshMode> = combine(
        isOnline,
        wallet,
        realtimeProvider.subscribedWalletId,
    ) { online, wallet, subscribedWalletId ->
        when {
            !online -> FundingRefreshMode.OFFLINE
            wallet != null && wallet.id == subscribedWalletId -> FundingRefreshMode.REALTIME
            else -> FundingRefreshMode.POLLING
        }
    }.distinctUntilChanged()

    private val fundingActivityRefreshSignal: Flow<Unit> = if (awaitsFundingActivity) {
        fundingRefreshMode.flatMapLatest { mode ->
            if (mode == FundingRefreshMode.POLLING) {
                pendingActivitiesTicker().take(PENDING_REFRESH_FAST_TICKS)
            } else {
                emptyFlow()
            }
        }
    } else {
        emptyFlow()
    }

    private val fundingOutcomes = Channel<Unit>(Channel.CONFLATED)

    private val fundingOutcomeRefreshSignal: Flow<Unit> = fundingOutcomes.receiveAsFlow()
        .flatMapLatest {
            fundingRefreshMode.flatMapLatest { mode ->
                when (mode) {
                    FundingRefreshMode.OFFLINE -> emptyFlow()
                    FundingRefreshMode.REALTIME -> flowOf(Unit)
                    FundingRefreshMode.POLLING -> flow {
                        emit(Unit)
                        emitAll(pendingActivitiesTicker().take(PENDING_REFRESH_FAST_TICKS))
                    }
                }
            }
        }

    internal val activitiesRefreshSignal: Flow<Unit> = merge(
        pendingActivitiesRefreshSignal,
        realtimeRefreshSignal,
        perpsFundingRefreshSignal,
        fundingActivityRefreshSignal,
        fundingOutcomeRefreshSignal,
    )

    val resolvedNfts: StateFlow<Map<String, HistoryNft>> = nftResolver.nfts

    val resolvedPerpsMarkets: StateFlow<Map<Int, PerpMarket>> = perpsMarketResolver.markets

    internal val typeSelector: StateFlow<EventsTypeSelectorState> =
        combine(typeFilter, perpsAllowed, ::typeSelectorState)
            .cacheState(
                initialValue = typeSelectorState(typeFilter.value, perpsAllowed = perpsAllowed.value),
            )

    private val queryFlow = combine(
        wallet.filterNotNull(),
        chainFilter,
        combine(typeFilter, perpsAllowed) { filter, allowed -> filter to allowed },
        accountRepoLegacy.selectedWalletFlow,
        combine(
            settingsRepository.hideDustActivitiesFlow,
            settingsRepository.hiddenBalancesFlow,
        ) { hideDust, _ -> hideDust to settingsRepository.hiddenBalances },
    ) { wallet, chain, (typeFilter, perpsAllowed), legacyWallet, (hideDust, hiddenBalance) ->
        EventsQuery(
            walletId = wallet.id,
            chain = chain,
            assetId = assetId,
            typeFilter = typeFilter,
            tonNetwork = legacyWallet.network,
            hideDust = hideDust && mode == EventsScreenMode.General,
            showPerps = typeFilter.requestsPerps && perpsAllowed,
            hiddenBalance = hiddenBalance,
        )
    }

    init {
        bgScope.launch {
            queryFlow
                .flowMap { query -> query.walletId.takeIf { query.showPerps } }
                .distinctUntilChanged()
                .flatMapLatest { walletId ->
                    walletId?.let(fundingRecovery::observeNewOutcomes) ?: emptyFlow()
                }
                .collect { fundingOutcomes.trySend(Unit) }
        }
    }

    private val depositStatuses: StateFlow<List<PerpsDepositStatus>> = queryFlow
        .flowMap { query -> query.walletId.takeIf { query.showPerps } }
        .distinctUntilChanged()
        .flatMapLatest { walletId ->
            if (walletId == null) {
                flowOf(emptyList())
            } else {
                fundingRecovery.observe(walletId).flowMap { records -> records.mapNotNull { it.toDepositStatus() } }
            }
        }
        .cacheState(initialValue = emptyList())

    internal val depositOrigins: StateFlow<List<PerpsDepositOriginRecord>> = queryFlow
        .flowMap { query -> query.walletId.takeIf { query.showPerps } }
        .distinctUntilChanged()
        .flatMapLatest { walletId ->
            if (walletId == null) {
                flowOf(emptyList())
            } else {
                fundingRecovery.observe(walletId)
                    .flowMap { records -> records.filter { it.stage == PerpsDepositStage.CREDITED } }
                    .distinctUntilChanged()
                    .mapLatest { records -> records.mapNotNull { record -> depositOriginOf(walletId, record) } }
            }
        }
        .cacheState(initialValue = emptyList())

    private val openedDepositId = MutableStateFlow<String?>(null)

    val openedDeposit: StateFlow<PerpsDepositStatusUi?> = combine(depositStatuses, openedDepositId) { deposits, id ->
        id?.let { deposits.firstOrNull { deposit -> deposit.id == it } }
    }
        .toStatusUi(hiddenBalance = { settingsRepository.hiddenBalances })
        .cacheState(initialValue = null)

    private val activitySelection = MutableStateFlow<ActivitySelection?>(null)

    val openedActivity: StateFlow<WalletActivityListItem.Entry?> = activitySelection
        .flowMap { it?.shown }
        .cacheState(initialValue = null)

    val activitiesFlow: Flow<PagingData<WalletActivityListItem>> = queryFlow
        .flatMapLatest(::createActivitiesFlow)
        .cachedIn(viewModelScope)

    val hideDust: StateFlow<Boolean> = settingsRepository.hideDustActivitiesFlow

    fun openDeposit(activity: HistoryEventEntity): Boolean {
        if (activity.perpsKind != PerpsEventKind.Deposit || !activity.isPerpsBalanceChange) {
            return false
        }
        if (activity.perpsBalanceChangeStatus == PerpsBalanceChangeStatus.Done) {
            return false
        }
        val hashes = activity.txIds.mapNotNullTo(HashSet()) { perpsTransactionHash(it) }
        val deposit = depositStatuses.value.firstOrNull { deposit -> deposit.transactions.any(hashes::contains) }
            ?: return false
        openedDepositId.value = deposit.id
        return true
    }

    fun closeDeposit() {
        openedDepositId.value = null
    }

    fun openActivity(entry: WalletActivityListItem.Entry, shownItems: List<WalletActivityListItem>) {
        val shownHistory = shownItems.mapNotNull { (it as? WalletActivityListItem.Entry)?.activity }
        activitySelection.value = ActivitySelection(
            opened = OpenedHistoryEvent(event = entry.activity, shownHistory = shownHistory),
            shown = entry,
        )
    }

    fun closeActivity() {
        activitySelection.value = null
    }

    private fun syncOpenedActivity(loaded: List<HistoryEventEntity>, hiddenBalance: Boolean) {
        activitySelection.update { selection ->
            val latest = selection?.opened?.latestVersionIn(loaded) ?: return@update selection
            selection.copy(shown = WalletActivityListItem.Entry(activity = latest, hiddenBalance = hiddenBalance))
        }
    }

    private suspend fun depositOriginOf(walletId: String, record: PerpsFundingRecord): PerpsDepositOriginRecord? {
        val status = record.toDepositStatus() ?: return null
        val units = record.sourceAmount.toBigIntegerOrNull() ?: return null
        val asset = try {
            accountRepo.findAccount(walletId, record.sourceAssetId)?.asset
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps history: no account for the deposit source ${record.sourceAssetId}")
            null
        } ?: return null
        return PerpsDepositOriginRecord(
            transactions = status.transactions,
            expectedCredit = status.creditedAmount ?: status.expectedAmount,
            viaAggregator = record.route == PerpsDepositRoute.CROSSCHAIN_SWAP,
            createdAt = status.createdAt,
            origin = PerpsDepositOrigin(asset = asset, amount = PerpsFundingAmount.toDecimal(units, asset.decimals)),
        )
    }

    fun onHideDustChanged() {
        settingsRepository.hideDustActivities = !settingsRepository.hideDustActivities
    }

    fun onChainSelected(chain: Chain?) {
        if (assetId != null) {
            return
        }
        chainFilter.value = chain
    }

    internal fun onTypeFilterSelected(index: Int) {
        typeSelector.value.entries.getOrNull(index)?.let { typeFilter.value = it }
    }

    private fun typeSelectorState(
        selected: EventsTypeFilter,
        perpsAllowed: Boolean,
    ): EventsTypeSelectorState {
        val entries = EventsTypeFilter.visible(perpsAllowed)
        return EventsTypeSelectorState(
            items = entries.map { application.getString(it.titleRes) },
            selectedIndex = entries.indexOf(selected).coerceAtLeast(0),
            selected = selected,
            entries = entries,
        )
    }

    fun invalidateCache() {
        eventsRepository.invalidateFirstPageCache()
    }

    fun onScreenStarted() {
        if (!reportsScreenRender) {
            return
        }
        screenRender.lifecycle(ScreenRenderLifecycle.WillShow, ScreenRenderId.History, this)
        screenRender.lifecycle(ScreenRenderLifecycle.Shown, ScreenRenderId.History, this)
    }

    fun onScreenStopped() {
        if (reportsScreenRender) {
            screenRender.abandon(ScreenRenderId.History, this)
        }
    }

    private fun reportInitialLoad(error: Throwable?) {
        if (!reportsScreenRender) {
            return
        }
        screenRender.requestFinished(ScreenRenderId.History, this)
        if (error == null) {
            screenRender.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.History, this)
        } else {
            screenRender.failed(ScreenRenderId.History, ScreenRenderErrors.kind(error), this)
        }
    }

    private fun createActivitiesFlow(
        query: EventsQuery,
    ): Flow<PagingData<WalletActivityListItem>> = flow {
        hasAnyActivity.value = null
        hasPendingActivities.value = false
        val locale = settingsRepository.getLocale()

        val pager = Pager(
            config = PagingConfig(
                initialLoadSize = 25,
                pageSize = 25,
                prefetchDistance = 5,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                EventsPagingSource(
                    repository = eventsRepository,
                    walletId = query.walletId,
                    chain = query.chain?.toApiChain(),
                    assetId = query.assetId,
                    typeFilter = query.typeFilter,
                    hideDust = query.hideDust,
                    showPerps = query.showPerps,
                    onInitialLoad = { hasAnyActivity.value = it },
                    onInitialRequestStarted = {
                        if (reportsScreenRender) {
                            screenRender.requestStarted(ScreenRenderId.History, this@EventsFeature)
                        }
                    },
                    onInitialRequestFinished = { error -> reportInitialLoad(error) },
                    onPageLoaded = { activities, isInitialLoad ->
                        if (isInitialLoad) {
                            hasPendingActivities.value = activities.any { it.isPending }
                            syncOpenedActivity(activities, query.hiddenBalance)
                        }
                        if (activities.isNotEmpty()) {
                            viewModelScope.launch {
                                nftResolver.resolve(activities, query.tonNetwork)
                            }
                            viewModelScope.launch {
                                perpsMarketResolver.resolve(activities)
                            }
                        }
                    },
                )
            },
        )

        emitAll(
            pager.flow.flowMap { data ->
                withDateSectionHeaders(
                    data = data,
                    application = application,
                    locale = locale,
                    hiddenBalance = query.hiddenBalance,
                )
            },
        )
    }

    private fun pendingActivitiesTicker(): Flow<Unit> = flow {
        var elapsedTicks = 0
        while (true) {
            delay(pendingRefreshDelay(elapsedTicks))
            elapsedTicks++
            emit(Unit)
        }
    }

    private fun pendingRefreshDelay(elapsedTicks: Int): Long {
        return if (elapsedTicks < PENDING_REFRESH_FAST_TICKS) {
            PENDING_REFRESH_INTERVAL_MS
        } else {
            PENDING_REFRESH_SLOW_INTERVAL_MS
        }
    }

    private fun withDateSectionHeaders(
        data: PagingData<HistoryEventEntity>,
        application: Application,
        locale: Locale,
        hiddenBalance: Boolean,
    ): PagingData<WalletActivityListItem> {
        return data.map { WalletActivityListItem.Entry(activity = it, hiddenBalance = hiddenBalance) }
            .insertSeparators { before, after ->
                after ?: return@insertSeparators null

                val afterMillis = after.activity.blockTime.toInstant().toEpochMilli()
                val beforeMillis = before?.activity?.blockTime?.toInstant()?.toEpochMilli()

                val afterKey = WalletActivityDateHeaderUtils.headerKeyCalendar(afterMillis)
                val beforeKey = beforeMillis?.let(WalletActivityDateHeaderUtils::headerKeyCalendar)

                if (beforeKey == afterKey) {
                    return@insertSeparators null
                }

                WalletActivityListItem.DateHeader(
                    firstActivityKey = after.activity.listKey,
                    title = WalletActivityDateHeaderUtils.formatGroupDateLabel(
                        context = application,
                        epochMillis = afterMillis,
                        locale = locale,
                    ),
                )
            }
    }
}

private enum class FundingRefreshMode {
    OFFLINE,
    REALTIME,
    POLLING,
}
