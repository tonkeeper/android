package com.tonapps.trading.screens.shelves

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.perps.PerpsFragment
import com.tonapps.trading.AssetsFragment
import io.tradingapi.models.AssetsTab
import io.tradingapi.models.MarketListKey
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.androidx.compose.koinViewModel
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation

class ShelvesFragment : ComposableFragment() {

    private val scrollToShelfFlow = MutableStateFlow<MarketListKey?>(null)

    fun scrollToShelf(key: String) {
        val parsed = MarketListKey.decode(key) ?: return
        scrollToShelfFlow.value = parsed
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            val viewModel = koinViewModel<ShelvesFeature>()
            val scrollToShelfKey by scrollToShelfFlow.collectAsState()
            val isMultichainWallet by viewModel.isMultichainWallet.collectAsState()
            ShelvesScreen(
                feature = viewModel,
                scrollToShelfKey = scrollToShelfKey,
                onScrollToShelfHandled = { scrollToShelfFlow.value = null },
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
                        delegate?.onOpenPortfolioSearch(sort, network)
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
                onOpenPerps = { navigation?.add(PerpsFragment.newInstance()) },
                onOpenPerpMarket = { marketIndex, symbol ->
                    navigation?.add(PerpsFragment.newInstance(marketIndex, symbol))
                },
                onOpenLink = { url -> delegate?.onOpenLink(url) },
            )
        }
    }
}
