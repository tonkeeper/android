package com.tonapps.blockchain.model.transaction

import com.tonapps.blockchain.model.account.Account
import com.tonapps.blockchain.model.account.Asset
import java.math.BigInteger

sealed interface Transaction {

    val account: Account
    val amount: BigInteger
    val mainAsset: Asset get() = account.asset

    data class Trade(
        override val account: Account,
        override val amount: BigInteger,
        val destination: Account,
        val to: String? = null,
        val meta: String? = null,
        val isMax: Boolean = false,
        val energy: Asset = account.asset,
        val routingFee: Fee.Value? = null,
    ) : Transaction

    data class Transfer(
        override val account: Account,
        override val amount: BigInteger,
        val to: String,
        val isMax: Boolean,
        val meta: String? = null,
        val energy: Asset = account.asset,
    ) : Transaction

    data class Call(
        override val account: Account,
        override val amount: BigInteger,
        val contract: String,
        val data: String,
        val energy: Asset = account.asset,
    ) : Transaction
}
