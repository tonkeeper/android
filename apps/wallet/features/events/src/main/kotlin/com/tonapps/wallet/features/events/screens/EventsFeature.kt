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
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.network.NetworkMonitor
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.toApiChain
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.HistoryNft
import com.tonapps.wallet.features.events.data.HistoryNftResolver
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
)

internal data class EventsTypeSelectorState(
    val items: List<String>,
    val selectedIndex: Int,
    val selected: EventsTypeFilter,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EventsFeature(
    private val application: Application,
    private val eventsRepository: McEventsRepository,
    private val nftResolver: HistoryNftResolver,
    private val accountRepoLegacy: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
    private val realtimeProvider: McWalletRealtimeProvider,
    private val assetId: String? = null,
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
    private val typeFilter = MutableStateFlow(EventsTypeFilter.All)

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

    internal val activitiesRefreshSignal: Flow<Unit> = merge(pendingActivitiesRefreshSignal, realtimeRefreshSignal)

    val resolvedNfts: StateFlow<Map<String, HistoryNft>> = nftResolver.nfts

    internal val typeSelector: StateFlow<EventsTypeSelectorState> = typeFilter
        .flowMap(::typeSelectorState)
        .cacheState(initialValue = typeSelectorState(EventsTypeFilter.All))

    private val queryFlow = combine(
        wallet.filterNotNull(),
        chainFilter,
        typeFilter,
        accountRepoLegacy.selectedWalletFlow,
        settingsRepository.hideDustActivitiesFlow,
    ) { wallet, chain, typeFilter, legacyWallet, hideDust ->
        EventsQuery(
            walletId = wallet.id,
            chain = chain,
            assetId = assetId,
            typeFilter = typeFilter,
            tonNetwork = legacyWallet.network,
            hideDust = hideDust,
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
        EventsTypeFilter.entries.getOrNull(index)?.let { typeFilter.value = it }
    }

    private fun typeSelectorState(selected: EventsTypeFilter) = EventsTypeSelectorState(
        items = EventsTypeFilter.entries.map { application.getString(it.titleRes) },
        selectedIndex = EventsTypeFilter.entries.indexOf(selected).coerceAtLeast(0),
        selected = selected,
    )

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
                    onInitialLoad = { hasAnyActivity.value = it },
                    onPageLoaded = { activities, isInitialLoad ->
                        if (isInitialLoad) {
                            hasPendingActivities.value = activities.any { it.isPending }
                        }
                        if (activities.isNotEmpty()) {
                            viewModelScope.launch {
                                nftResolver.resolve(activities, query.tonNetwork)
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
    ): PagingData<WalletActivityListItem> {
        return data.map { WalletActivityListItem.Entry(it) }
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
                    sectionKey = afterKey,
                    title = WalletActivityDateHeaderUtils.formatGroupDateLabel(
                        context = application,
                        epochMillis = afterMillis,
                        locale = locale,
                    ),
                )
            }
    }
}
