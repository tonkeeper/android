package com.tonapps.trading

import com.tonapps.bus.generated.Events.TradeUiFlow.TradeUiFlowFrom
import java.util.concurrent.atomic.AtomicBoolean

object TradeEntryTracker {

    private val fromDeepLink = AtomicBoolean(false)

    fun markDeepLink() {
        fromDeepLink.set(true)
    }

    fun consumeFrom(): TradeUiFlowFrom {
        return if (fromDeepLink.get()) {
            TradeUiFlowFrom.DeepLink
        } else {
            TradeUiFlowFrom.TabBar
        }
    }
}
