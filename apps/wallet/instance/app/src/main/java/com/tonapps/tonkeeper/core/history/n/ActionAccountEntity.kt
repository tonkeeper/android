package com.tonapps.tonkeeper.core.history.n

import android.os.Parcelable
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.tonkeeper.core.history.recipient
import com.tonapps.tonkeeper.core.history.sender
import io.tonapi.models.AccountAddress
import kotlinx.parcelize.Parcelize

@Parcelize
data class ActionAccountEntity(
    val address: String,
    val name: String?,
    val isWallet: Boolean,
    val icon: String?,
    val isScam: Boolean,
): Parcelable {

    constructor(account: AccountAddress, testnet: Boolean) : this(
        address = account.address.toUserFriendly(
            wallet = account.isWallet,
            testnet = testnet,
        ),
        name = account.name,
        isWallet = account.isWallet,
        icon = account.icon,
        isScam = account.isScam
    )

    companion object {

        fun ofSender(action: io.tonapi.models.Action, testnet: Boolean): ActionAccountEntity? {
            return action.sender?.let { ActionAccountEntity(it, testnet) }
        }

        fun ofRecipient(action: io.tonapi.models.Action, testnet: Boolean): ActionAccountEntity? {
            return action.recipient?.let { ActionAccountEntity(it, testnet) }
        }
    }
}