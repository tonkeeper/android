package com.tonapps.tonkeeper.manager.tx.model

import io.tonapi.models.AccountEvent

data class PendingWrapEvent(
    val hash: PendingHash,
    val event: AccountEvent
)