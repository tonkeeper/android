package com.tonapps.wallet.data.events.entities

import android.os.Parcelable
import io.tonapi.models.AccountEvent
import kotlinx.parcelize.Parcelize

@Parcelize
data class EventEntity(
    val id: String,
    val timestamp: Long,
    val lt: Long,
    val isScam: Boolean,
    val inProgress: Boolean,
    val fee: Long,
    val actions: List<ActionEntity>
): Parcelable {

    constructor(model: AccountEvent, testnet: Boolean): this(
        id = model.eventId,
        timestamp = model.timestamp,
        lt = model.lt,
        isScam = model.isScam,
        inProgress = model.inProgress,
        fee = model.extra,
        actions = ActionEntity.map(model.actions, testnet)
    )
}