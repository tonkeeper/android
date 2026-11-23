package com.tonapps.wallet.data.ledger.analytics

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerFlowOutcome
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationOutcome
import com.tonapps.ledgerkit.controller.LedgerOperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class LedgerRequestAnalytics internal constructor(controller: LedgerController, scope: CoroutineScope) {

    init {
        scope.launch {
            controller.results.collect(::track)
        }
    }

    internal fun track(result: LedgerOperationResult) {
        LedgerNonFatals.reportRequest(result)
        val failure = result.lastFailure
        AnalyticsHelper.Default.events.ledgerFlow.ledgerRequestResult(
            purpose = result.request.purpose.toLedgerFlowPurpose(),
            outcome = result.toOutcome(),
            attempts = result.attempts,
            durationMs = result.durationMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            failureType = failure?.toFailureType(),
            ledgerApp = (failure?.appId ?: result.app)?.toLedgerFlowApp(),
            ledgerSetting = (failure as? LedgerFailure.SettingRequired)?.setting?.toLedgerFlowSetting(),
            errorCode = failure?.statusWord,
            ledgerModel = result.model?.toLedgerFlowModel(),
            ledgerTransport = result.linkKind?.toLedgerFlowTransport(),
        )
    }

    private fun LedgerOperationResult.toOutcome(): LedgerFlowOutcome = when (outcome) {
        LedgerOperationOutcome.Success -> LedgerFlowOutcome.Success
        LedgerOperationOutcome.Cancelled -> LedgerFlowOutcome.Cancelled
        LedgerOperationOutcome.Failed -> if (lastFailure is LedgerFailure.UserRefused) {
            LedgerFlowOutcome.Rejected
        } else {
            LedgerFlowOutcome.Failed
        }
    }
}
