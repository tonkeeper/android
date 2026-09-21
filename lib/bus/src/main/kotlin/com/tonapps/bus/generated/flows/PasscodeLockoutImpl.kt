package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.PasscodeLockout.PasscodeLockoutFrom

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class PasscodeLockoutImpl(
    private val eventExecutor: EventExecutor,
) : Events.PasscodeLockout {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * passcode_lockout
     *
     * Fired when repeated failed passcode attempts trigger a brute-force lockout that temporarily disables passcode input. The failure counter is global to the single app passcode, so a lockout closes every passcode prompt at once (app unlock, in-app confirmations, change-passcode). One event per lockout, emitted the moment the lockout starts.

     */
    @AnyThread
    override fun passcodeLockout(from: PasscodeLockoutFrom, lockoutSeconds: Int, failedAttempts: Int) {
        val props = hashMapOf(
            "from" to from.key,
            "lockout_seconds" to lockoutSeconds,
            "failed_attempts" to failedAttempts
        )
        trackEvent("passcode_lockout", props)
    }
}
