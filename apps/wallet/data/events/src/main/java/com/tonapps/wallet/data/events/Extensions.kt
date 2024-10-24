package com.tonapps.wallet.data.events

import com.tonapps.blockchain.ton.extensions.equalsAddress
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.Action

val Action.isTransfer: Boolean
    get() {
        return type == Action.Type.tonTransfer || type == Action.Type.jettonTransfer || type == Action.Type.nftItemTransfer
    }

fun AccountEvent.isOutTransfer(accountId: String): Boolean {
    return actions.any { it.isOutTransfer(accountId) }
}

fun Action.isOutTransfer(accountId: String): Boolean {
    if (!isTransfer) {
        return false
    }
    val sender = tonTransfer?.sender ?: jettonTransfer?.sender ?: nftItemTransfer?.sender ?: return false
    return sender.address.equalsAddress(accountId)
}

val Action.recipient: AccountAddress?
    get() = tonTransfer?.recipient ?: jettonTransfer?.recipient ?: nftItemTransfer?.recipient ?: jettonSwap?.userWallet ?: jettonMint?.recipient ?: depositStake?.staker ?: withdrawStake?.staker ?: withdrawStakeRequest?.staker

val Action.sender: AccountAddress?
    get() = tonTransfer?.sender ?: jettonTransfer?.sender ?: nftItemTransfer?.sender ?: jettonSwap?.userWallet ?: jettonBurn?.sender ?: depositStake?.staker ?: withdrawStake?.staker ?: withdrawStakeRequest?.staker
