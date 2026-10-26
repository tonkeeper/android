package com.tonapps.wallet.api.entity

import io.tonapi.models.MessageConsequences

data class EmulateWithBatteryResult(
    val consequences: MessageConsequences,
    // The battery relays this message right now: it supports the operation and allows it.
    val withBattery: Boolean,
    val excess: Long?,
    // The battery relays this kind of message at all, whether or not it can pay for it right now.
    val supportedByBattery: Boolean = withBattery,
)
