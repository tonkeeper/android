package com.tonapps.tonkeeper.usecase.emulation

import com.tonapps.wallet.data.core.entity.TransferType

data class InsufficientBalance(
    val type: TransferType
) {
}