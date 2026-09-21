package com.tonapps.portfolio.screens.search

import android.os.Bundle
import android.view.View
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.core.navigation.PortfolioSearchSort
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uikit.extensions.activity

class SearchFragment : ComposableFragment() {

    override val fragmentName: String = "SearchFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        val featureData = SearchFeatureData.from(arguments)
        setContent {
            val feature = koinViewModel<SearchFeature> { parametersOf(featureData) }
            SearchScreen(
                feature = feature,
                onBack = { finish() },
                onOpenAsset = { asset ->
                    delegate?.onOpenAssetDetails(
                        assetId = asset.id,
                        previewName = asset.name,
                        previewImageUrl = asset.imageUrl,
                        from = AssetScreenFrom.WalletScreen
                    )
                },
                onOpenPerpMarket = { marketIndex, symbol ->
                    delegate?.onOpenPerpMarket(marketIndex, symbol)
                },
            )
        }
    }

    companion object {
        fun newInstance(
            initialSort: PortfolioSearchSort? = null,
            initialNetwork: Network.Type? = null,
        ): SearchFragment = SearchFragment().apply {
            arguments = Bundle().apply {
                putString(SearchFeatureData.ARG_INITIAL_SORT, initialSort?.name)
                putString(SearchFeatureData.ARG_INITIAL_NETWORK, initialNetwork?.name)
            }
        }
    }
}
