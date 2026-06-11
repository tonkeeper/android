package com.tonapps.blockchain.model.transaction

import java.math.BigInteger
import com.tonapps.blockchain.model.account.Account

data class TransactionInfo(
    val account: Account,
    val hash: String,
    val status: Status,
    val nonce: Int,
    /**
     * Timestamp in milliseconds from UTC epoch
     */
    val timestampMs: Long,
    val fee: BigInteger,
    val meta: String = "",
) {
    sealed interface Status {
        data object Confirmed : Status
        data object Pending : Status
        data class Failed(val error: String) : Status
    }
}
