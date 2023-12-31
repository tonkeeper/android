package com.tonkeeper.core.tonconnect.models

import com.tonkeeper.core.history.list.item.HistoryItem
import org.ton.contract.wallet.WalletTransfer

data class TCTransaction(
    val clientId: String,
    val id: String,
    val transfers: List<WalletTransfer>,
    val fee: String,
    val previewItems: List<HistoryItem>
)