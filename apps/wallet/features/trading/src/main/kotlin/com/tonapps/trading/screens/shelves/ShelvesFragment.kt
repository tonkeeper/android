package com.tonapps.trading.screens.shelves

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.core.screenrender.ScreenRenderFragmentLifecycle
import com.tonapps.trading.AssetsFragment
import io.tradingapi.models.AssetsTab
import io.tradingapi.models.MarketListKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.TabScreen
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation

class ShelvesFragment : ComposableFragment(), TabScreen {

    // Taken here rather than in Compose, so the tab can tell the feature it is shown again.
    private val feature: ShelvesFeature by viewModel()
    private val screenRender = ScreenRenderFragmentLifecycle(this, ScreenRenderId.Trade)

    private val scrollToShelfFlow = MutableStateFlow<MarketListKey?>(null)
    private val scrollToTopEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun scrollToShelf(key: String) {
        val parsed = MarketListKey.decode(key) ?: return
        scrollToShelfFlow.value = parsed
    }

    override fun smoothScrollToTop() {
        scrollToTopEvents.tryEmit(Unit)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        screenRender.onHiddenChanged(hidden)
        if (!hidden) {
            feature.onScreenShown()
            feature.reloadPerpsShelf()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            feature.reloadPerpsShelf()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        screenRender.onViewCreated()

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            val viewModel = feature
            val scrollToShelfKey by scrollToShelfFlow.collectAsState()
            val isMultichainWallet by viewModel.isMultichainWallet.collectAsState()
            ShelvesScreen(
                feature = viewModel,
                scrollToShelfKey = scrollToShelfKey,
                onScrollToShelfHandled = { scrollToShelfFlow.value = null },
                scrollToTop = scrollToTopEvents,
                onOpenSearch = {
                    if (isMultichainWallet) {
                        delegate?.onOpenPortfolioSearch()
                    } else {
                        navigation?.add(
                            AssetsFragment.newInstance(
                                focusSearch = true,
                                initialTab = AssetsTab.all,
                            )
                        )
                    }
                },
                onOpenSeeAll = { tab, sort, network ->
                    if (isMultichainWallet) {
                        delegate?.onOpenPortfolioSearch(sort, network, tab.toPortfolioSearchAssetType())
                    } else {
                        navigation?.add(AssetsFragment.newInstance(initialTab = tab))
                    }
                },
                onOpenAssetDetails = {
                    navigation?.add(
                        AssetsFragment.newInstance(
                            marketItem = it,
                            from = AssetScreenFrom.TradeScreen,
                        )
                    )
                },
                onOpenPerps = { delegate?.onOpenPerps(PerpsEntryPoint.TradeTab) },
                onOpenSearchCategory = { category -> delegate?.onOpenPortfolioSearch(initialAssetType = category) },
                onOpenPerpMarket = { marketIndex, symbol ->
                    delegate?.onOpenPerpMarket(marketIndex, symbol, PerpsEntryPoint.TradeTab)
                },
                onOpenLink = { url -> delegate?.onOpenLink(url) },
            )
        }
    }
}
