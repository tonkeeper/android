package com.tonapps.trading.screens.shelves

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.core.flags.WalletFeature
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.trading.TradeEntryTracker
import com.tonapps.trading.data.TradingRepository
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.banner.BannerRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.mvi.flow.flatMapLatestCatching
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.bannerEntity
import io.tradingapi.models.MarketItem
import io.tradingapi.models.MarketListKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
) : AsyncViewModel() {

    private val refreshSignal = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    private val multichainWalletFlow = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.type == WalletType.Multichain }
        .distinctUntilChanged()

    val isMultichainWallet: StateFlow<Boolean> = multichainWalletFlow
        .cacheState(initialValue = false)

    val isPerpsVisible: StateFlow<Boolean> = multichainWalletFlow
        .map { isMultichain -> isMultichain && WalletFeature.Perps.isEnabled }
        .cacheState(initialValue = false)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val perpsShelfMarkets: StateFlow<List<PerpMarket>> = refreshSignal
        .onStart { emit(false) }
        .flatMapLatest { soft ->
            isPerpsVisible.flatMapLatest { visible ->
                flow {
                    if (!visible) {
                        emit(emptyList())
                        return@flow
                    }
                    if (!soft) {
                        emit(emptyList())
                    }
                    val markets = perpsRepository.getMarkets("", PerpsMarketFilter.ALL, PerpsSort.VOLUME, USD_CURRENCY, cursor = null)
                        .getOrNull()
                        ?.markets
                        .orEmpty()
                        .take(PERPS_SHELF_SIZE)
                    emit(markets)
                }
            }
        }
        .cacheState(initialValue = emptyList())

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
                        emit(tradingRepository.getShelfGroups(isMultichain))
                    } catch (e: Throwable) {
                        L.e(e)
                        if (!soft) {
                            _shelvesError.value = true
                            emit(emptyList())
                        }
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

    fun refresh() {
        val soft = shelfGroupsFlow.value != null && !shelvesError.value
        bgScope.launch {
            tradingRepository.clearCache()
            refreshSignal.emit(soft)
        }
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

    private companion object {
        const val PERPS_SHELF_SIZE = 8
    }
}
