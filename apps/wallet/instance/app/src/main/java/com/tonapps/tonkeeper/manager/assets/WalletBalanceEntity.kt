package com.tonapps.tonkeeper.manager.assets

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.account.entities.WalletEntity

data class WalletBalanceEntity(
    val accountId: String,
    val testnet: Boolean,
    val balance: Coins
) {

    data class Balances(
        val balances: List<WalletBalanceEntity>
    ) {

        fun getBalance(wallet: WalletEntity): WalletBalanceEntity? {
            return balances.firstOrNull { it.accountId == wallet.accountId && it.testnet == wallet.testnet }
        }
    }
}