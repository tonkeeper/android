package com.tonapps.tonkeeper.ui.screen.send.state

import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import io.tonapi.models.Account
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
    ): SendDestination() {

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
            isBounce = isBounce(query, account)
        )
    }

    data object Empty: SendDestination()
    data object NotFound: SendDestination()

}