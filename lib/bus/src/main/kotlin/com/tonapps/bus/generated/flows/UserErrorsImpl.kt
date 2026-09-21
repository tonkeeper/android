package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.UserErrors.UserErrorsSeverity

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class UserErrorsImpl(
    private val eventExecutor: EventExecutor,
) : Events.UserErrors {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * custom_error
     *
     * A custom error was captured by the app
     */
    @AnyThread
    override fun customError(
        severity: UserErrorsSeverity,
        errorMessage: String,
        errorCode: String?,
        otherMetadata: String?
    ) {
        val props = hashMapOf<String, Any>("severity" to severity.key, "error_message" to errorMessage)
        errorCode?.let { props["error_code"] = it }
        otherMetadata?.let { props["other_metadata"] = it }
        trackEvent("custom_error", props)
    }
}
