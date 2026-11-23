package com.tonapps.ledgerkit

class LedgerFallbackException(message: String, cause: Throwable? = null) : Exception(message, cause)

object LedgerDiagnostics {

    @Volatile
    var sink: (Throwable) -> Unit = {}

    fun report(issue: Throwable) {
        sink(issue)
    }
}
