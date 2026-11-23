package com.tonapps.wallet.data.ledger.signing

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import kotlinx.coroutines.CancellationException

class LedgerDismissedException(val failure: LedgerFailure) : CancellationException("Ledger request ended: $failure")

suspend fun <T> ledgerCall(block: suspend () -> T): T = try {
    block()
} catch (e: LedgerFailureException) {
    if (e.isPresented || e.failure == LedgerFailure.Cancelled) {
        throw LedgerDismissedException(e.failure)
    }
    throw e
}
