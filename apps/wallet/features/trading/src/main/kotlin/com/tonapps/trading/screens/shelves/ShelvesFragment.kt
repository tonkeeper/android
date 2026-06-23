package com.tonapps.trading.screens.shelves

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.trading.AssetsFragment
import io.tradingapi.models.MarketListKey
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.androidx.compose.koinViewModel
import uikit.navigation.Navigation.Companion.navigation

class ShelvesFragment : ComposableFragment() {

    private val scrollToShelfFlow = MutableStateFlow<MarketListKey?>(null)

    fun scrollToShelf(key: String) {
        val parsed = MarketListKey.decode(key) ?: return
        scrollToShelfFlow.value = parsed
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setContent {
            val viewModel = koinViewModel<ShelvesFeature>()
            val scrollToShelfKey by scrollToShelfFlow.collectAsState()
            ShelvesScreen(
                feature = viewModel,
                scrollToShelfKey = scrollToShelfKey,
                onScrollToShelfHandled = { scrollToShelfFlow.value = null },
                onOpenAssets = { navigation?.add(AssetsFragment.newInstance(initialTab = it)) },
                onOpenSearch = { navigation?.add(AssetsFragment.newInstance(focusSearch = true)) },
                onOpenAssetDetails = {
                    navigation?.add(
                        AssetsFragment.newInstance(
                            marketItem = it,
                            from = AssetScreenFrom.TradeScreen,
                        )
                    )
                },
            )
        }
    }
}
