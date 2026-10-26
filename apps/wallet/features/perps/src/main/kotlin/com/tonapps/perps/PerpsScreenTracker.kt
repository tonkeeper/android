package com.tonapps.perps

import java.util.concurrent.atomic.AtomicReference

class PerpsScreenTracker {

    private val visible = AtomicReference<PerpsRoutes?>(null)

    fun isShowing(marketIndex: Int?): Boolean {
        return when (val route = visible.get()) {
            is PerpsRoutes.Portfolio -> marketIndex == null
            is PerpsRoutes.Details -> route.marketIndex == marketIndex
            else -> false
        }
    }

    internal fun show(route: PerpsRoutes) {
        visible.set(route)
    }

    internal fun hide(route: PerpsRoutes) {
        visible.compareAndSet(route, null)
    }
}
