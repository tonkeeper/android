package com.tonapps.tonkeeper.core.history.n

import com.tonapps.tonkeeper.core.history.ActionType
import com.tonapps.wallet.data.account.entities.WalletEntity

data class ActionEntity(
    val index: Int,
    val eventId: String,
    val wallet: WalletEntity,
    val type: ActionType,
    val sender: ActionAccountEntity?,
    val recipient: ActionAccountEntity?,
    val timestamp: Long,
) {
}