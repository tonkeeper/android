package com.tonapps.deposit.screens.send.state

import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.WalletEntity

data class TonTransaction(
    val fromWallet: WalletEntity,
    val destination: SendDestination.TonAccount,
    val token: BalanceEntity,
    val comment: String?,
    val amount: Amount,
    val encryptedComment: Boolean,
    val max: Boolean
) {

    data class Amount(
        val value: Coins = Coins.ZERO,
        val converted: Coins = Coins.ZERO,
        val format: CharSequence = "",
        val convertedFormat: CharSequence = "",
    ) {

        val isEmpty: Boolean
            get() = !value.isPositive
    }
}