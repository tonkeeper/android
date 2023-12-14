package com.tonkeeper.core.transaction

import com.tonkeeper.core.history.list.item.HistoryItem

data class TransactionEmulate(
    val fee: Long,
    val actions: List<HistoryItem>
)