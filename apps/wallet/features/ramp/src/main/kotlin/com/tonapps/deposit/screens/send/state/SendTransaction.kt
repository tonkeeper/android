package com.tonapps.deposit.screens.send.state

import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.WalletEntity

data class SendTransaction(
    val fromWallet: WalletEntity,
    val destination: SendDestination,
    val token: BalanceEntity,
    val comment: String?,
    val amount: Amount,
    val encryptedComment: Boolean,
    val max: Boolean
) {

    fun isRealMax(balance: Coins): Boolean {
        return amount.value >= balance
    }

    data class Amount(
        val value: Coins,
        val converted: Coins,
        val format: CharSequence,
        val convertedFormat: CharSequence,
    )
}