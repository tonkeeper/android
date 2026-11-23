package com.tonapps.perps

import android.os.Bundle
import android.view.View
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.core.ComposableFragment
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.core.helper.navigationDelegate
import com.tonapps.perps.data.PerpsRealtime
import com.tonapps.wallet.data.multichain.asset.perpsAssetId
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment
import uikit.extensions.activity

class PerpsFragment : ComposableFragment(), BaseFragment.SwipeBack {

    override val fragmentName: String = "PerpsFragment"

    private val realtime: PerpsRealtime by inject()

    private val realtimeDemand by lazy { realtime.demand() }

    interface Delegate {
        fun onOpenPerpTrade(symbol: String)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            arguments?.getString(ARG_ENTRY_POINT)
                ?.let { name -> PerpsEntryPoint.entries.firstOrNull { it.name == name } }
                ?.let(PerpsAnalytics::sectionOpened)
        }
    }

    override fun onStart() {
        super.onStart()
        realtimeDemand.set(true)
    }

    override fun onStop() {
        realtimeDemand.set(false)
        super.onStop()
    }

    override fun onDestroy() {
        realtimeDemand.close()
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? Delegate
        val navDelegate = context?.navigationDelegate

        val marketIndex = arguments?.getInt(ARG_MARKET_INDEX, NO_MARKET) ?: NO_MARKET
        val symbol = arguments?.getString(ARG_SYMBOL)
        val initial = when {
            marketIndex != NO_MARKET -> listOf(
                PerpsRoutes.Details(marketIndex, symbol, arguments?.getBoolean(ARG_AUTO_CLOSE) == true)
            )
            else -> listOf(PerpsRoutes.Portfolio)
        }

        setContent {
            PerpsRouter(
                initial = initial,
                onTrade = { tradeSymbol -> delegate?.onOpenPerpTrade(tradeSymbol) },
                onOpenHistory = { navDelegate?.onOpenPerpsHistory() },
                onOpenMarketHistory = { marketIndex ->
                    navDelegate?.onOpenAssetHistory(perpsAssetId(marketIndex))
                },
                onOpenFundingHistory = { navDelegate?.onOpenPerpsHistory(awaitsFundingActivity = true) },
                onClose = { finish() },
            )
        }
    }

    companion object {
        private const val ARG_MARKET_INDEX = "market_index"
        private const val ARG_SYMBOL = "symbol"
        private const val ARG_AUTO_CLOSE = "auto_close"
        private const val ARG_ENTRY_POINT = "entry_point"
        private const val NO_MARKET = -1

        fun newInstance(entryPoint: PerpsEntryPoint): PerpsFragment =
            PerpsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ENTRY_POINT, entryPoint.name)
                }
            }

        fun newInstance(marketIndex: Int, symbol: String, entryPoint: PerpsEntryPoint): PerpsFragment =
            PerpsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MARKET_INDEX, marketIndex)
                    putString(ARG_SYMBOL, symbol)
                    putString(ARG_ENTRY_POINT, entryPoint.name)
                }
            }

        fun newInstance(arguments: Bundle): PerpsFragment =
            PerpsFragment().apply { this.arguments = Bundle(arguments) }

        fun newInstance(route: DeepLinkRoute.Perps, entryPoint: PerpsEntryPoint): PerpsFragment = when (route) {
            is DeepLinkRoute.Perps.Home, is DeepLinkRoute.Perps.MarketSymbol -> newInstance(entryPoint)
            is DeepLinkRoute.Perps.Market -> newMarketLink(route.marketIndex, autoClose = false, entryPoint)
            is DeepLinkRoute.Perps.AutoClose -> newMarketLink(route.marketIndex, autoClose = true, entryPoint)
        }

        private fun newMarketLink(marketIndex: Int, autoClose: Boolean, entryPoint: PerpsEntryPoint): PerpsFragment =
            PerpsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MARKET_INDEX, marketIndex)
                    putBoolean(ARG_AUTO_CLOSE, autoClose)
                    putString(ARG_ENTRY_POINT, entryPoint.name)
                }
            }
    }
}
