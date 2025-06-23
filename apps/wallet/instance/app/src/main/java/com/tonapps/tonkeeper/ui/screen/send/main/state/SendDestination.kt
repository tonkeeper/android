package com.tonapps.tonkeeper.ui.screen.send.main.state

import com.tonapps.blockchain.ton.extensions.isTestnetAddress
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.wallet.api.entity.Blockchain
import com.tonapps.wallet.api.entity.TokenEntity
import io.tonapi.models.AccountStatus
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd

sealed class SendDestination {

    data class TronAccount(val address: String) : SendDestination()

    data class TokenError(
        val addressBlockchain: Blockchain,
        val selectedToken: TokenEntity,
    ) : SendDestination()

    data class TonAccount(
        val query: String,
        val publicKey: PublicKeyEd25519,
        val address: AddrStd,
        val memoRequired: Boolean,
        val isSuspended: Boolean,
        val isWallet: Boolean,
        val name: String?,
        val isScam: Boolean,
        val isBounce: Boolean,
        val existing: Boolean,
        val testnet: Boolean
    ) : SendDestination() {

        companion object {
            private fun isBounce(query: String, account: io.tonapi.models.Account): Boolean {
                if (account.status != AccountStatus.active && (query.startsWith("EQ") || query.isTestnetAddress())) {
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
                return address.toString(
                    userFriendly = true,
                    testOnly = testnet,
                    bounceable = isBounce
                )
            }

        constructor(
            query: String,
            publicKey: PublicKeyEd25519,
            account: io.tonapi.models.Account,
            testnet: Boolean
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
            existing = (account.status == AccountStatus.active || account.status == AccountStatus.frozen),
            testnet = testnet
        )
    }

    data object Empty : SendDestination()
    data object NotFound : SendDestination()

}