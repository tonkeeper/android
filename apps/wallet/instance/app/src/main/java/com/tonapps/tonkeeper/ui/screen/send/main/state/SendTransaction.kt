package com.tonapps.tonkeeper.ui.screen.send.main.state

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.account.entities.WalletEntity

data class SendTransaction(
    val fromWallet: WalletEntity,
    val destination: SendDestination.Account,
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