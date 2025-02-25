package com.tonapps.tonkeeper.ui.screen.send.main.state

import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import io.tonapi.models.AccountStatus
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd

sealed class SendDestination {

    data class Account(
        val query: String,
        val publicKey: PublicKeyEd25519,
        val address: AddrStd,
        val memoRequired: Boolean,
        val isSuspended: Boolean,
        val isWallet: Boolean,
        val name: String?,
        val isScam: Boolean,
        val isBounce: Boolean,
        val existing: Boolean
    ) : SendDestination() {

        companion object {
            private fun isBounce(query: String, account: io.tonapi.models.Account): Boolean {
                if (account.status != AccountStatus.active && query.startsWith("EQ")) {
                    return false
                }
                val bounce = query.startsWith("EQ") || !query.startsWith("U")
                if (!query.isValidTonAddress()) {
                    return !account.isWallet
                }
                return bounce
            }
        }

        val displayName: String?
            get() {
                return if (query.isValidTonAddress()) {
                    name
                } else {
                    query.lowercase()
                }
            }

        val displayAddress: String
            get() {
                return address.toString(userFriendly = true, bounceable = isBounce)
            }

        constructor(
            query: String,
            publicKey: PublicKeyEd25519,
            account: io.tonapi.models.Account
        ) : this(
            query = query,
            publicKey = publicKey,
            address = AddrStd(account.address),
            memoRequired = account.memoRequired ?: false,
            isSuspended = account.isSuspended ?: false,
            isWallet = account.isWallet,
            name = account.name,
            isScam = account.isScam ?: false,
            isBounce = isBounce(query, account),
            existing = (account.status == AccountStatus.active || account.status == AccountStatus.frozen)
        )
    }

    data object Empty : SendDestination()
    data object NotFound : SendDestination()

}