package com.tonapps.trading.screens.shelves

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.screenrender.ScreenRenderErrorKind
import com.tonapps.bus.screenrender.ScreenRenderErrors
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.bus.screenrender.ScreenRenderStage
import com.tonapps.bus.screenrender.ScreenRenderTracker
import com.tonapps.core.flags.WalletFeature
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.trading.TradeEntryTracker
import com.tonapps.trading.data.TradingRepository
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.banner.BannerRepository
import com.tonapps.wallet.data.loyalty.LoyaltyEntry
import com.tonapps.wallet.data.loyalty.LoyaltyRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.mvi.flow.flatMapLatestCatching
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.bannerEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tradingapi.models.MarketItem
import io.tradingapi.models.MarketListKey
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

data class ShelfSelection(
    val networkName: String? = null,
    val tabKey: MarketListKey? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShelvesFeature(
    private val tradingRepository: TradingRepository,
    private val accountRepository: AccountRepository,
    private val bannerRepository: BannerRepository,
    private val raffleRepository: RaffleRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val perpsRepository: PerpsRepository,
    private val loyaltyRepository: LoyaltyRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    private val refreshSignal = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val perpsShelfReload = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val screenShown = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val multichainWalletFlow = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.type == WalletType.Multichain }
        .distinctUntilChanged()

    val isMultichainWallet: StateFlow<Boolean> = multichainWalletFlow
        .cacheState(initialValue = false)

    val isPerpsVisible: StateFlow<Boolean> = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.supportsPerps == true && WalletFeature.Perps.isEnabled }
        .distinctUntilChanged()
        .cacheState(initialValue = false)

    val loyalty: StateFlow<LoyaltyEntry> = combine(
        unifiedAccountRepository.selectedTonWalletFlow
            .map { wallet -> wallet?.takeIf { it.type == WalletType.Multichain }?.id }
            .distinctUntilChanged(),
        merge(refreshSignal.map { }, screenShown).onStart { emit(Unit) },
    ) { walletId, _ -> walletId }
        .flatMapLatest { walletId ->
            walletId?.let(loyaltyRepository::entryFlow) ?: flowOf(LoyaltyEntry.Hidden)
        }
        .cacheState(initialValue = LoyaltyEntry.Hidden)

    val hiddenBalances: StateFlow<Boolean> = settingsRepository.hiddenBalancesFlow
        .map { settingsRepository.hiddenBalances }
        .cacheState(initialValue = settingsRepository.hiddenBalances)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val perpsShelfMarkets: StateFlow<List<PerpMarket>> = isPerpsVisible
        .flatMapLatest { visible ->
            if (visible) {
                merge(refreshSignal.map { }, perpsShelfReload)
                    .onStart { emit(Unit) }
                    .mapLatest { perpsRepository.getShelfMarkets() }
                    .runningFold(emptyList<PerpMarket>()) { previous, loaded -> loaded.ifEmpty { previous } }
            } else {
                flowOf(emptyList())
            }
        }
        .cacheState(initialValue = emptyList())

    private val screenRender = ScreenRenderTracker.shared

    @Volatile
    private var shelvesErrorKind: ScreenRenderErrorKind? = null

    private val _shelvesError = MutableStateFlow(false)
    val shelvesError: StateFlow<Boolean> = _shelvesError.asStateFlow()

    val favoritesFlow = tradingRepository.favoriteAssetsFlow

    val selections: SnapshotStateMap<String, ShelfSelection> = mutableStateMapOf()

    // The trading screen builds its raffle promo from the raffle endpoint
    // (one banner per raffle); the wallet screen instead gets its raffle banner
    // through the shared banners feed, so that feed isn't reused here.
    val raffleBanners: StateFlow<List<BannerEntity>> = accountRepository.selectedWalletFlow
        .flatMapLatestCatching { wallet ->
            raffleRepository.getRafflesFlow(wallet.id, forced = false)
                .map { raffles ->
                    bannerRepository.filterHidden(wallet.id, raffles.mapNotNull { it.bannerEntity() })
                }
        }
        .cacheState(initialValue = emptyList())

    val raffleCells: StateFlow<List<ShelvesRaffleCellItem>> = accountRepository.selectedWalletFlow
        .flatMapLatestCatching { wallet -> raffleRepository.getRafflesFlow(wallet.id, forced = false) }
        .transformLatest { raffles ->
            while (true) {
                val now = OffsetDateTime.now()
                emit(
                    raffles
                        .filter { it.hasLeaderboard && it.hasStarted(now) && !it.hasEnded(now) }
                        .map { it.toShelvesRaffleCellItem() },
                )
                val nextBoundary = raffles
                    .flatMap { listOf(it.startsAt, it.endsAt) }
                    .filter { it.isAfter(now) }
                    .minOrNull()
                    ?: break
                delay(Duration.between(OffsetDateTime.now(), nextBoundary).toMillis().coerceAtLeast(0))
            }
        }
        .cacheState(initialValue = emptyList())

    val shelfGroupsFlow = refreshSignal
        .onStart { emit(false) }
        .flatMapLatest { soft ->
            multichainWalletFlow.flatMapLatest { isMultichain ->
                flow {
                    _shelvesError.value = false
                    _isRefreshing.value = true
                    if (!soft) {
                        emit(null)
                    }
                    try {
                        var isFetched = false
                        val groups = tradingRepository.getShelfGroups(
                            isMultichainWallet = isMultichain,
                            onFetchStarted = {
                                isFetched = true
                                screenRender.requestStarted(ScreenRenderId.Trade)
                            },
                            onFetchFinished = { screenRender.requestFinished(ScreenRenderId.Trade) },
                        )
                        emit(groups)
                        shelvesErrorKind = null
                        reportShelvesShown(isFresh = isFetched)
                    } catch (e: CancellationException) {
                        // A newer load replaced this one (flatMapLatest); it reports for the session.
                        throw e
                    } catch (e: Throwable) {
                        L.e(e)
                        if (!soft) {
                            _shelvesError.value = true
                            emit(emptyList())
                        }
                        val kind = ScreenRenderErrors.kind(e)
                        shelvesErrorKind = kind
                        screenRender.failed(ScreenRenderId.Trade, kind)
                    } finally {
                        _isRefreshing.value = false
                    }
                }
            }
        }
        .cacheState()

    init {
        AnalyticsHelper.Default.events.tradeUiFlow.tradeStarted(from = TradeEntryTracker.consumeFrom())
    }

    // Shelves are cached in memory for the process; a cached answer needs no request.
    private fun reportShelvesShown(isFresh: Boolean) {
        if (isFresh) {
            screenRender.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Trade)
        } else {
            screenRender.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.Trade)
            screenRender.finish(ScreenRenderId.Trade)
        }
    }

    /** The tab is shown again. Shelf groups load once, so the visit ends with what is on screen or the last failure. */
    fun onScreenShown() {
        screenShown.tryEmit(Unit)
        val groups = shelfGroupsFlow.value ?: return
        if (groups.isNotEmpty()) {
            screenRender.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.Trade)
        }
        val errorKind = shelvesErrorKind
        if (errorKind != null) {
            screenRender.failed(ScreenRenderId.Trade, errorKind)
        } else {
            screenRender.finish(ScreenRenderId.Trade)
        }
    }

    fun refresh() {
        val soft = shelfGroupsFlow.value != null && !shelvesError.value
        bgScope.launch {
            tradingRepository.clearCache()
            refreshSignal.emit(soft)
        }
    }

    fun reloadPerpsShelf() {
        perpsShelfReload.tryEmit(Unit)
    }

    fun onNetworkSelected(groupId: String, networkName: String) {
        val existing = selections[groupId] ?: ShelfSelection()
        selections[groupId] = existing.copy(networkName = networkName)
    }

    fun onTabSelected(groupId: String, tabKey: MarketListKey) {
        val existing = selections[groupId] ?: ShelfSelection()
        selections[groupId] = existing.copy(tabKey = tabKey)
    }

    fun selectShelf(groupId: String, networkName: String?, tabKey: MarketListKey) {
        selections[groupId] = ShelfSelection(networkName = networkName, tabKey = tabKey)
    }

    fun hideBanner(banner: BannerEntity) {
        val walletId = accountRepository.getSelectedWalletId() ?: return
        bgScope.launch {
            bannerRepository.hideBanner(walletId, banner.id)
        }
    }

    fun trackAssetClick(item: MarketItem) {
        AnalyticsHelper.Default.events.tradeUiFlow.tradeClickAsset(
            from = TradeEntryTracker.consumeFrom(),
            asset = item.asset.id
        )
    }

    fun removeFavorites(assetIds: Collection<String>) {
        if (assetIds.isEmpty()) {
            return
        }
        bgScope.launch {
            assetIds.forEach { assetId ->
                try {
                    tradingRepository.removeFavoriteAsset(assetId)
                } catch (e: Throwable) {
                    L.e(e)
                }
            }
        }
    }
}
