package com.tonapps.tonkeeper.core.history

data class TransactionDetails(
    val iconUrl: String? = null,
    val isOut: Boolean = false,
    val title: String,
    val date: String,
    val accountName: String? = null,
    val accountAddress: String? = null,
    val fee: Float,
    val comment: String? = null,
)