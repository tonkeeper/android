package com.tonapps.perps

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import uikit.extensions.activity

class PerpsFragment : ComposableFragment() {

    override val fragmentName: String = "PerpsFragment"

    interface Delegate {
        fun onOpenPerpTrade(symbol: String)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? Delegate

        val marketIndex = arguments?.getInt(ARG_MARKET_INDEX, NO_MARKET) ?: NO_MARKET
        val symbol = arguments?.getString(ARG_SYMBOL)
        val initial = when {
            marketIndex != NO_MARKET && symbol != null -> PerpsRoutes.Details(marketIndex, symbol)
            else -> PerpsRoutes.Portfolio
        }

        setContent {
            PerpsRouter(
                initial = initial,
                onTrade = { tradeSymbol -> delegate?.onOpenPerpTrade(tradeSymbol) },
                onClose = { finish() },
            )
        }
    }

    companion object {
        private const val ARG_MARKET_INDEX = "market_index"
        private const val ARG_SYMBOL = "symbol"
        private const val NO_MARKET = -1

        fun newInstance(): PerpsFragment = PerpsFragment()

        fun newInstance(marketIndex: Int, symbol: String): PerpsFragment =
            PerpsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MARKET_INDEX, marketIndex)
                    putString(ARG_SYMBOL, symbol)
                }
            }
    }
}
