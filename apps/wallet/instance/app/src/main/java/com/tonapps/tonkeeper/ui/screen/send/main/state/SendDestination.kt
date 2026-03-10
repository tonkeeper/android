package com.tonapps.tonkeeper.ui.screen.send.main.state

import com.tonapps.blockchain.ton.TonAddressTags
import com.tonapps.wallet.api.entity.value.Blockchain
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
        val userInput: String,
        val isUserInputAddress: Boolean,
        val publicKey: PublicKeyEd25519,
        val address: AddrStd,
        val memoRequired: Boolean,
        val isSuspended: Boolean,
        val isWallet: Boolean,
        val name: String?,
        val existing: Boolean,
        val testnet: Boolean,
        val tonAddressTags: TonAddressTags,
        val isBounce: Boolean,
    ) : SendDestination() {

        companion object {

            private fun isBounce(
                tonAddressTags: TonAddressTags,
                isUserInputAddress: Boolean,
                account: io.tonapi.models.Account
            ): Boolean {
                if (account.status != AccountStatus.active && (!tonAddressTags.isBounceable || tonAddressTags.isTestnet == true)) {
                    return false
                }
                if (!isUserInputAddress) {
                    return !account.isWallet
                }
                return tonAddressTags.isBounceable
            }
        }

        val displayName: String? by lazy {
            if (isUserInputAddress) name else userInput.lowercase()
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
            userInput: String,
            isUserInputAddress: Boolean,
            publicKey: PublicKeyEd25519,
            account: io.tonapi.models.Account,
            testnet: Boolean,
            tonAddressTags: TonAddressTags
        ) : this(
            userInput = userInput,
            isUserInputAddress = isUserInputAddress,
            publicKey = publicKey,
            address = AddrStd(account.address),
            memoRequired = account.memoRequired ?: false,
            isSuspended = account.isSuspended ?: false,
            isWallet = account.isWallet,
            name = account.name,
            existing = (account.status == AccountStatus.active || account.status == AccountStatus.frozen),
            testnet = testnet,
            tonAddressTags = tonAddressTags,
            isBounce = isBounce(tonAddressTags, isUserInputAddress, account)
        )
    }

    data object Scam : SendDestination()
    data object Empty : SendDestination()
    data object NotFound : SendDestination()

}