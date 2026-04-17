package com.tonapps.wallet.data.tx.model

import io.tonapi.models.AccountEvent

data class PendingWrapEvent(
    val hash: PendingHash,
    val event: AccountEvent
)