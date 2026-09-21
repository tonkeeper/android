package com.tonapps.trading

import com.tonapps.bus.generated.Events.TradeUiFlow.TradeStartedFrom
import java.util.concurrent.atomic.AtomicBoolean

object TradeEntryTracker {

    private val fromDeepLink = AtomicBoolean(false)

    fun markDeepLink() {
        fromDeepLink.set(true)
    }

    fun consumeFrom(): TradeStartedFrom {
        return if (fromDeepLink.get()) {
            TradeStartedFrom.DeepLink
        } else {
            TradeStartedFrom.TabBar
        }
    }
}
