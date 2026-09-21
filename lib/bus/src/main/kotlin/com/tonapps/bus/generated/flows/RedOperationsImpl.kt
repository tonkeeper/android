package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsFlow
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOutcome

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class RedOperationsImpl(
    private val eventExecutor: EventExecutor,
) : Events.RedOperations {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * op_attempt
     *
     * Emitted once per user operation attempt. Use same operation_id in the corresponding op_terminal event.
     */
    @AnyThread
    override fun opAttempt(
        operationId: String,
        flow: RedOperationsFlow,
        operation: RedOperationsOperation,
        attemptSource: String?,
        startedAtMs: Int,
        otherMetadata: String?
    ) {
        val props = hashMapOf<String, Any>(
            "operation_id" to operationId,
            "flow" to flow.key,
            "operation" to operation.key,
            "started_at_ms" to startedAtMs
        )
        attemptSource?.let { props["attempt_source"] = it }
        otherMetadata?.let { props["other_metadata"] = it }
        trackEvent("op_attempt", props)
    }

    /**
     * op_terminal
     *
     * Emitted exactly once per operation when it completes (success, fail, or cancel). Must share operation_id with the corresponding op_attempt.
     */
    @AnyThread
    override fun opTerminal(
        operationId: String,
        flow: RedOperationsFlow,
        operation: RedOperationsOperation,
        outcome: RedOperationsOutcome,
        durationMs: Double,
        finishedAtMs: Int,
        errorCode: Int?,
        errorMessage: String?,
        errorType: String?,
        stage: String?,
        otherMetadata: String?
    ) {
        val props = hashMapOf<String, Any>(
            "operation_id" to operationId,
            "flow" to flow.key,
            "operation" to operation.key,
            "outcome" to outcome.key,
            "duration_ms" to durationMs,
            "finished_at_ms" to finishedAtMs
        )
        errorCode?.let { props["error_code"] = it }
        errorMessage?.let { props["error_message"] = it }
        errorType?.let { props["error_type"] = it }
        stage?.let { props["stage"] = it }
        otherMetadata?.let { props["other_metadata"] = it }
        trackEvent("op_terminal", props)
    }
}
