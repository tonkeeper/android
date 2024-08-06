package com.tonapps.tonkeeper.ui.screen.send.state

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.account.entities.WalletEntity

data class SendTransaction(
    val fromWallet: WalletEntity,
    val destination: SendDestination.Account,
    val token: BalanceEntity,
    val comment: String?,
    val amount: Amount,
    val encryptedComment: Boolean
) {

    data class Amount(
        val value: Coins,
        val converted: Coins,
        val format: CharSequence,
        val convertedFormat: CharSequence,
    )
}