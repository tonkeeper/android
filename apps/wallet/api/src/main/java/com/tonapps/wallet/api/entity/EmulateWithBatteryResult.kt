package com.tonapps.wallet.api.entity

import io.tonapi.models.MessageConsequences

data class EmulateWithBatteryResult(
    val consequences: MessageConsequences,
    val withBattery: Boolean,
    val excess: Long?,
)