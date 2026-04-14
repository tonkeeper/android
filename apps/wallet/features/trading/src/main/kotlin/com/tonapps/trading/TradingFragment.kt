package com.tonapps.trading

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import com.tonapps.trading.screens.shelves.ShelvesFeature
import com.tonapps.trading.screens.shelves.ShelvesScreen
import org.koin.androidx.compose.koinViewModel

class TradingFragment : ComposableFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setContent {
            val viewModel = koinViewModel<ShelvesFeature>()
            ShelvesScreen(
                feature = viewModel,
                onOpenAssets = { /* TODO */ },
                onOpenAssetDetails = { /* TODO */ },
            )
        }
    }
}
