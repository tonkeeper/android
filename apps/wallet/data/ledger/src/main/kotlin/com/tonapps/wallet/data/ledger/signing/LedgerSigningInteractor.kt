package com.tonapps.wallet.data.ledger.signing

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import org.ton.cell.Cell
import kotlin.time.TimeMark
import com.tonapps.ledger.ton.Transaction as LedgerTonTransaction

interface LedgerSigningInteractor {

    suspend fun signTransactions(walletId: String, requests: List<LedgerTransactionRequest>): List<ByteArray>

    suspend fun signMessage(walletId: String, account: Account, type: MessageType, data: String): String

    suspend fun signTonTransactions(walletId: String, transactions: List<LedgerTonTransaction>): List<Cell>

    suspend fun signTonTransaction(walletId: String, transaction: LedgerTonTransaction, step: Int, totalSteps: Int): Cell

    suspend fun signTonProof(walletId: String, domain: String, timestamp: Long, payload: String): ByteArray

    suspend fun signTronTransaction(walletId: String, transaction: TronTransaction): TronTransaction

    suspend fun signSolanaTransactions(walletId: String, account: Account, transactions: List<String>): List<String>

    suspend fun verifyAddress(walletId: String, chain: Chain)
}

data class LedgerTransactionRequest(
    val transaction: Transaction,
    val fee: Fee,
    val nonce: BigInteger,
    val signableUntil: TimeMark? = null,
)
