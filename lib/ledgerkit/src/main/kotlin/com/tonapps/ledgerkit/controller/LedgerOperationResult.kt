package com.tonapps.ledgerkit.controller

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel

data class LedgerOperationResult(
    val request: LedgerRequest,
    val outcome: LedgerOperationOutcome,
    val attempts: Int,
    val durationMs: Long,
    val lastFailure: LedgerFailure?,
    val errors: List<LedgerFailureException>,
    val app: LedgerAppId?,
    val model: LedgerModel?,
    val linkKind: LedgerLinkKind?,
)

enum class LedgerOperationOutcome {
    Success,
    Failed,
    Cancelled,
}
