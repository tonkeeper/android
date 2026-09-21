package com.tonapps.wallet.data.multichain.tx

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.log.L
import com.tonapps.wallet.PendingTransactionReporter
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.toApiChain
import io.JsonAny
import io.walletapi.models.ActivityType
import io.walletapi.models.PendingTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.tonapps.chainkit.core.chain.model.account.Network as ChainNetwork
import io.walletapi.models.Chain as ApiChain
import io.walletapi.models.Network as ApiNetwork

class RemotePendingTransactionReporter(
    private val api: API,
) : PendingTransactionReporter {

    override suspend fun onTransactionSent(
        walletId: String,
        tx: Transaction,
        txHash: String,
        service: String?,
        routeId: String?,
        serviceTxId: String?,
        sellBaseAmount: BigInteger?,
    ) {
        val chain = tx.account.chain
        val apiChain = chain.toApiChain() ?: return

        addPendingTransaction(
            walletId = walletId,
            pendingTransaction = PendingTransaction(
                chain = apiChain,
                network = chain.network.mode.toApiNetwork(),
                txHash = txHash,
                activityType = tx.toActivityType(),
                payload = tx.toPayload(service, routeId, serviceTxId, sellBaseAmount)
            ),
        )
    }

    suspend fun onTronTransactionSent(walletId: String, txHash: String) {
        addPendingTransaction(
            walletId = walletId,
            pendingTransaction = PendingTransaction(
                chain = ApiChain.tron,
                network = ApiNetwork.mainnet,
                txHash = txHash,
                activityType = ActivityType.send,
                payload = null
            ),
        )
    }

    private suspend fun addPendingTransaction(
        walletId: String,
        pendingTransaction: PendingTransaction,
    ) {
        withContext(Async.Io) {
            try {
                api.multichain.wallets.addPendingTransactions(
                    walletId = walletId,
                    pendingTransaction = pendingTransaction,
                    xWalletId = walletId,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e("McPendingTransactionReporter", "Failed to report pending transaction", e)
            }
        }
    }

    private fun Transaction.toPayload(
        service: String?,
        routeId: String?,
        serviceTxId: String?,
        sellBaseAmount: BigInteger?,
    ): Map<String, JsonAny>? {
        return when (this) {
            is Transaction.Swap -> {
                if (sellBaseAmount == null) {
                    L.e("McPendingTransactionReporter", "Reporting a pending swap without a sell amount")
                }
                mapOf(
                    "from_asset_id" to JsonAny.Primitive(account.asset.id),
                    "to_asset_id" to JsonAny.Primitive(destination.asset.id),
                    "aggregator" to JsonAny.Primitive(service),
                    "route_id" to JsonAny.Primitive(routeId),
                    "provider_route_id" to JsonAny.Primitive(serviceTxId),
                    "from_amount" to JsonAny.Primitive(sellBaseAmount?.toString()),
                    "from_address" to JsonAny.Primitive(account.addressDisplay),
                )
            }
            else -> null
        }
    }

    private fun ChainNetwork.Mode.toApiNetwork(): ApiNetwork {
        return when (this) {
            ChainNetwork.Mode.Mainnet -> ApiNetwork.mainnet
            ChainNetwork.Mode.Testnet -> ApiNetwork.testnet
        }
    }

    private fun Transaction.toActivityType(): ActivityType {
        return when (this) {
            is Transaction.Swap -> ActivityType.swap
            is Transaction.Transfer -> ActivityType.send
            is Transaction.Call -> ActivityType.contract_call
            is Transaction.Staking.Stake,
            is Transaction.Staking.Restake,
            is Transaction.Staking.Compound -> ActivityType.stake
            is Transaction.Staking.Unstake -> ActivityType.unstake
            is Transaction.Staking.Claim -> ActivityType.claim
        }
    }
}
