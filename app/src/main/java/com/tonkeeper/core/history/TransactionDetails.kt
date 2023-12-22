package com.tonkeeper.core.history

data class TransactionPreview(
    val iconUrl: String? = null,
    val token: String,
    val amount: Float,
) {
}