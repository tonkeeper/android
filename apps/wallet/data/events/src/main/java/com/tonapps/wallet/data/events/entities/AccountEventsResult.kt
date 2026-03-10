package com.tonapps.wallet.data.events.entities

import io.tonapi.models.AccountEvents

data class AccountEventsResult(
    val cache: Boolean = false,
    val events: AccountEvents,
)