package com.tonapps.wallet.data.events.entities

import io.tonapi.models.AccountAddress

data class LatestRecipientEntity(
    val account: AccountAddress,
    val timestamp: Long
)
