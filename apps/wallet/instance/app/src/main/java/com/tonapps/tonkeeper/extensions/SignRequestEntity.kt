package com.tonapps.tonkeeper.extensions

import com.tonapps.wallet.data.core.entity.SignRequestEntity
import org.ton.contract.wallet.WalletTransfer

suspend fun SignRequestEntity.getTransfers(): List<WalletTransfer> {
    return messages.map { it.getWalletTransfer() }
}