package com.tonapps.wallet.data.events.tx

import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.data.events.tx.model.TxEvent

data class TxPage(
    val source: Source,
    val events: List<TxEvent>,
    val beforeTimestamp: Timestamp?,
    val afterTimestamp: Timestamp?,
    val limit: Int
) {

    enum class Source {
        LOCAL, REMOTE
    }

    val isEmpty: Boolean
        get() = events.isEmpty()

    val isCached: Boolean
        get() = source == Source.LOCAL

    val nextKey: Timestamp?
        get() = events.minByOrNull { it.timestamp.value }?.timestamp
}