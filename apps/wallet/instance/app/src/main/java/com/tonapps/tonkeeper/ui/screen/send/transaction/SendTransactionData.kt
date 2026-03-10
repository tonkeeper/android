package com.tonapps.tonkeeper.ui.screen.send.transaction

import org.ton.contract.wallet.WalletTransfer

data class SendTransactionData(
    val transfers: List<WalletTransfer>,
    val seqNo: Int,
    val validUntil: Long
) {
}