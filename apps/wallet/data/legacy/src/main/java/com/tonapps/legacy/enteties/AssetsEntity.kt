package com.tonapps.legacy.enteties

import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.token.entities.AccountTokenEntity


sealed class AssetsEntity(
    val fiat: Coins,
) {
    data class Staked(val staked: StakedEntity): AssetsEntity(staked.fiatBalance) {
        val isTonstakers: Boolean
            get() = staked.isTonstakers

        val liquidToken: BalanceEntity?
            get() = staked.liquidToken

        val readyWithdraw: Coins
            get() = staked.readyWithdraw
    }

    data class Token(
        val token: AccountTokenEntity
    ): AssetsEntity(token.fiat) {

        val address: String
            get() = token.address

        val decimals: Int
            get() = token.decimals

        val balance: Coins
            get() = token.balance.value

        val symbol: String
            get() = token.symbol

        val blockchain: Blockchain
            get() = token.balance.token.blockchain

        constructor(token: TokenEntity): this(
            AccountTokenEntity.createEmpty(token, "")
        )
    }

    data class Currency(
        val currency: WalletCurrency,
        val coins: Coins = Coins.ZERO
    ): AssetsEntity(coins)
}