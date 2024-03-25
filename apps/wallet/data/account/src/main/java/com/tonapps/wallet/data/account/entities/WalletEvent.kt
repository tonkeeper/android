package com.tonapps.wallet.data.account.entities

import org.json.JSONObject

sealed class WalletEvent(
    open val wallet: WalletEntity
) {

    data class Boc(
        override val wallet: WalletEntity,
        val boc: String,
    ): WalletEvent(wallet) {

        constructor(wallet: WalletEntity, json: JSONObject) : this(
            wallet = wallet,
            boc = json.getString("boc"),
        )
    }

    data class Transaction(
        override val wallet: WalletEntity,
        val accountId: String,
        val lt: Long,
        val txHash: String
    ): WalletEvent(wallet) {

        constructor(wallet: WalletEntity, json: JSONObject) : this(
            wallet = wallet,
            accountId = json.getString("account_id"),
            lt = json.getLong("lt"),
            txHash = json.getString("tx_hash")
        )
    }
}