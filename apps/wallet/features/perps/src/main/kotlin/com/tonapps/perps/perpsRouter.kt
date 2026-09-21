package com.tonapps.perps

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.perps.screens.details.PerpsAssetDetailsFeature
import com.tonapps.perps.screens.details.PerpsAssetDetailsScreen
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.screens.markets.PerpsMarketsFeature
import com.tonapps.perps.screens.markets.PerpsMarketsScreen
import com.tonapps.perps.screens.portfolio.PerpsPortfolioFeature
import com.tonapps.perps.screens.portfolio.PerpsPortfolioScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav
import ui.moon.rememberNestedNavBackStack

@Serializable
sealed interface PerpsRoutes : NavKey {

    @Serializable
    data object Portfolio : PerpsRoutes

    @Serializable
    data class Markets(
        val filter: PerpsMarketFilter = PerpsMarketFilter.ALL,
    ) : PerpsRoutes

    @Serializable
    data class Details(
        val marketIndex: Int,
        val symbol: String,
    ) : PerpsRoutes
}

@Composable
fun PerpsRouter(
    initial: PerpsRoutes = PerpsRoutes.Portfolio,
    onTrade: (symbol: String) -> Unit,
    onClose: () -> Unit,
) {
    val backStack = rememberNestedNavBackStack(initial, onClose)

    MoonNav(backStack = backStack) { key ->
        when (key) {
            is PerpsRoutes.Portfolio -> NavEntry(key) {
                val feature = koinViewModel<PerpsPortfolioFeature>()
                PerpsPortfolioScreen(
                    feature = feature,
                    onOpenMarket = { marketIndex, symbol ->
                        backStack.add(PerpsRoutes.Details(marketIndex, symbol))
                    },
                    onOpenExplore = { backStack.add(PerpsRoutes.Markets()) },
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                )
            }

            is PerpsRoutes.Markets -> NavEntry(key) {
                val feature = koinViewModel<PerpsMarketsFeature>(
                    parameters = { parametersOf(key.filter) },
                )
                PerpsMarketsScreen(
                    feature = feature,
                    onOpenMarket = { marketIndex, symbol ->
                        backStack.add(PerpsRoutes.Details(marketIndex, symbol))
                    },
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                )
            }

            is PerpsRoutes.Details -> NavEntry(key) {
                val feature = koinViewModel<PerpsAssetDetailsFeature>(
                    parameters = { parametersOf(key.marketIndex, key.symbol) },
                )
                PerpsAssetDetailsScreen(
                    feature = feature,
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                )
            }

            else -> throw IllegalStateException("Unknown key: $key")
        }
    }
}
