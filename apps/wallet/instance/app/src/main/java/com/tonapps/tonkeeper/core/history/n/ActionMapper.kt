package com.tonapps.tonkeeper.core.history.n

import com.tonapps.tonkeeper.core.history.ActionType
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.tonapi.models.AccountEvent

object ActionMapper {


    fun map(wallet: WalletEntity, events: List<AccountEvent>) {

    }

    private fun map(wallet: WalletEntity, event: AccountEvent): EventEntity {
        val testnet = wallet.testnet
        val actions = mutableListOf<ActionEntity>()
        for ((index, action) in event.actions.withIndex()) {
            val sender = ActionAccountEntity.ofSender(action, testnet)
            val recipient = ActionAccountEntity.ofRecipient(action, testnet)

            actions.add(ActionEntity(
                index = index,
                eventId = event.eventId,
                wallet = wallet,
                type = ActionType.Fee,
                sender = sender,
                recipient = recipient,
                timestamp = event.timestamp
            ))
        }

        return EventEntity(actions)
    }
}