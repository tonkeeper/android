package com.tonapps.wallet.api

import android.util.Log
import com.tonapps.blockchain.Coin
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import io.tonapi.apis.AccountsApi
import io.tonapi.apis.JettonsApi
import io.tonapi.models.JettonBalance

object TonapiHelper {

    fun jettons(testnet: Boolean): JettonsApi {
        return Tonapi.jettons.get(testnet)
    }

    fun accounts(testnet: Boolean): AccountsApi {
        return Tonapi.accounts.get(testnet)
    }

    fun getTonBalance(
        accountId: String,
        testnet: Boolean
    ): BalanceEntity {
        val account = accounts(testnet).getAccount(accountId)
        return BalanceEntity(TokenEntity.TON, Coin.toCoins(account.balance))
    }

    fun getJettonsBalances(
        accountId: String,
        testnet: Boolean,
        currency: String
    ): List<BalanceEntity> {
        val jettonsBalances = accounts(testnet).getAccountJettonsBalances(
            accountId = accountId,
            currencies = currency
        ).balances
        return jettonsBalances.map { BalanceEntity(it) }
    }

}