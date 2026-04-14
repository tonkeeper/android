package com.tonapps.bus.generated

import com.tonapps.bus.generated.Events.RedOperations.RedOperationsFlow
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOutcome
import kotlin.coroutines.cancellation.CancellationException

fun Events.RedOperations.opTerminal(
    operationId: String,
    flow: RedOperationsFlow,
    operation: RedOperationsOperation,
    durationMs: Double,
    finishedAtMs: Int,
    error: Throwable?,
    stage: String? = null,
    otherMetadata: String? = null,
) {
    val outcome: RedOperationsOutcome
    val errorCode: Int?
    val errorMessage: String?
    val errorType: String?

    when {
        error == null -> {
            outcome = RedOperationsOutcome.Success
            errorCode = null
            errorMessage = null
            errorType = null
        }

        error is CancellationException -> {
            outcome = RedOperationsOutcome.Cancel
            errorCode = null
            errorMessage = error.message
            errorType = error::class.simpleName
        }

        else -> {
            outcome = RedOperationsOutcome.Fail
            errorCode = null
            errorMessage = error.message
            errorType = error::class.simpleName
        }
    }

    opTerminal(
        operationId = operationId,
        flow = flow,
        operation = operation,
        outcome = outcome,
        durationMs = durationMs,
        finishedAtMs = finishedAtMs,
        errorCode = errorCode,
        errorMessage = errorMessage,
        errorType = errorType,
        stage = stage,
        otherMetadata = otherMetadata,
    )
}
