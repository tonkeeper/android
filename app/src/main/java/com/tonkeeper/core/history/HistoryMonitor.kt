package com.tonkeeper.core.history

import com.tonkeeper.event.WalletStateUpdateEvent
import core.EventBus
import core.network.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

class HistoryMonitor(
    scope: CoroutineScope,
    accountId: String
) {

    private companion object {
        private const val PREFIX = "https://tonapi.io/v2/sse"
    }

    private val mempool = Network.subscribe("${PREFIX}/mempool?accounts=${accountId}")
    private val tx = Network.subscribe("${PREFIX}/accounts/transactions?accounts=${accountId}")

    init {
        merge(mempool, tx).onEach {
            EventBus.post(WalletStateUpdateEvent)
        }.launchIn(scope)
    }

}