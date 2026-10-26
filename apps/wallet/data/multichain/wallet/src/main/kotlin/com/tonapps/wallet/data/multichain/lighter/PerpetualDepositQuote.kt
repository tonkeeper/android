package com.tonapps.wallet.data.multichain.lighter

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.op.OpError
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute

data class PerpetualDepositQuote(
    val quoteId: String,
    val executionId: String?,
    val route: PerpsDepositRoute,
    val expiresAtMs: Long,
    val sourceAmount: BigInteger,
    val depositAmount: BigInteger,
    val operation: Operation,
    /**
     * A change_pub_key preview's: [operation] is signatures only, nothing to broadcast, and they go
     * back in the change_pub_key of the next quote.
     */
    val preview: Boolean = false,
    val estimatedDurationSeconds: Long? = null,
)

sealed interface PerpsQuoteError {
    data class MalformedAmount(val field: String) : PerpsQuoteError
    data class InvalidOperation(val error: OpError) : PerpsQuoteError

    /** An operation of a kind this quote was not asked for, or a payload its kind does not carry. */
    data class UnexpectedOperation(val type: String) : PerpsQuoteError

    /** A quote asked for again whose [field] no longer matches the one the user confirmed. */
    data class Changed(val field: String) : PerpsQuoteError
}
