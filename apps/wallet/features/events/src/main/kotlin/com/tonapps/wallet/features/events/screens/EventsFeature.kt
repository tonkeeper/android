package com.tonapps.wallet.features.events.screens

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.flags.WalletFeature
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.network.NetworkMonitor
import com.tonapps.perps.data.PerpMarket
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.toApiChain
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.HistoryNft
import com.tonapps.wallet.features.events.data.HistoryNftResolver
import com.tonapps.wallet.features.events.data.HistoryPerpsMarketResolver
import com.tonapps.wallet.features.events.data.McEventsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
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
    private val assetId: String? = null,
    private val mode: EventsScreenMode = EventsScreenMode.General,
    awaitsFundingActivity: Boolean = false,
) : AsyncViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnlineFlow
        .cacheState(initialValue = true)

    init {
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
                assetId == null && wallet?.type == WalletType.Multichain && WalletFeature.Perps.isEnabled
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

    private val fundingActivityRefreshSignal: Flow<Unit> = if (awaitsFundingActivity) {
        isOnline.flatMapLatest { online ->
            if (online) {
                pendingActivitiesTicker().take(PENDING_REFRESH_FAST_TICKS)
            } else {
                emptyFlow()
            }
        }
    } else {
        emptyFlow()
    }

    internal val activitiesRefreshSignal: Flow<Unit> =
        merge(pendingActivitiesRefreshSignal, realtimeRefreshSignal, fundingActivityRefreshSignal)

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

    val activitiesFlow: Flow<PagingData<WalletActivityListItem>> = queryFlow
        .flatMapLatest(::createActivitiesFlow)
        .cachedIn(viewModelScope)

    val hideDust: StateFlow<Boolean> = settingsRepository.hideDustActivitiesFlow

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
                    onPageLoaded = { activities, isInitialLoad ->
                        if (isInitialLoad) {
                            hasPendingActivities.value = activities.any { it.isPending }
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
