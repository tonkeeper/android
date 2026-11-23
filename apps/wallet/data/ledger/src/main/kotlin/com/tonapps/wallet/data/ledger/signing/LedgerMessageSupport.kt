package com.tonapps.wallet.data.ledger.signing

import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Chain

fun Chain.ledgerSignsMessage(type: MessageType): Boolean = when {
    isEvm -> type in EVM_MESSAGES
    this is Chain.Tron -> type == MessageType.Transaction || type == MessageType.Default
    this is Chain.Solana -> type == MessageType.Transaction
    else -> false
}

private val EVM_MESSAGES = setOf(
    MessageType.Legacy,
    MessageType.LegacyEip155,
    MessageType.Default,
    MessageType.TypedMessage,
    MessageType.TypedMessageEip155,
)
