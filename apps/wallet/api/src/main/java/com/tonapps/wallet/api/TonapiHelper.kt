package com.tonapps.wallet.api

import android.util.Log
import com.tonapps.blockchain.Coin
import com.tonapps.wallet.api.entity.AccountPreviewEntity
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import io.tonapi.apis.AccountsApi
import io.tonapi.apis.JettonsApi
import io.tonapi.apis.WalletApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519

object TonapiHelper {

    fun jettons(testnet: Boolean): JettonsApi {
        return Tonapi.jettons.get(testnet)
    }

    fun accounts(testnet: Boolean): AccountsApi {
        return Tonapi.accounts.get(testnet)
    }

    fun wallet(testnet: Boolean): WalletApi {
        return Tonapi.wallet.get(testnet)
    }

    fun getTonBalance(
        accountId: String,
        testnet: Boolean
    ): BalanceEntity {
        val account = accounts(testnet).getAccount(accountId)
        return BalanceEntity(TokenEntity.TON, Coin.toCoins(account.balance), accountId)
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
        return jettonsBalances.map { BalanceEntity(it) }.filter { it.value > 0 }
    }

    fun resolveAddressOrName(
        query: String,
        testnet: Boolean
    ): AccountPreviewEntity? {
        return try {
            val account = accounts(testnet).getAccount(query)
            AccountPreviewEntity(query, account)
        } catch (e: Throwable) {
            null
        }
    }

    fun resolvePublicKey(
        pk: PublicKeyEd25519,
        testnet: Boolean
    ): List<AccountPreviewEntity> {
        return try {
            val query = pk.key.hex()
            val wallets = wallet(testnet).getWalletsByPublicKey(query).accounts
            wallets.map { AccountPreviewEntity(query, it) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

}