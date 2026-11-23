package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderErrorKind
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderNetworkType
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderOutcome
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderScreen
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderThermalState
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderTrigger
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderWalletMode

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class ScreenRenderImpl(
    private val eventExecutor: EventExecutor,
) : Events.ScreenRender {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * screen_content_rendered
     *
     * How fast a screen shows content after the user action that opened it (first contentful paint). Sent once per opening of a screen, when the opening ends: fresh content is on screen, the load failed, the user left, the app went to the background, or 30 seconds passed. Every time is in milliseconds from that action on a monotonic clock, and a stage counts as shown at the first frame after its data reached the UI. On a cold launch (`trigger = app_launch`) the times count from the process start, and the time the user spent on the passcode screen is left out of all of them and reported in `paused_ms`. Prewarmed launches, launches that open onboarding, and launches whose own work took longer than 60 seconds are not sent. iOS only for now.

     */
    @AnyThread
    override fun screenContentRendered(
        screen: ScreenRenderScreen,
        walletMode: ScreenRenderWalletMode,
        trigger: ScreenRenderTrigger,
        outcome: ScreenRenderOutcome,
        errorKind: ScreenRenderErrorKind?,
        isFirstOpen: Boolean,
        cachedMs: Long?,
        freshMs: Long?,
        screenCreatedMs: Long?,
        screenWillShowMs: Long?,
        screenShownMs: Long?,
        requestMs: Long?,
        marks: String?,
        pausedMs: Long?,
        hadPasscode: Boolean?,
        networkType: ScreenRenderNetworkType,
        isLowPower: Boolean,
        thermalState: ScreenRenderThermalState
    ) {
        val props = hashMapOf<String, Any>(
            "screen" to screen.key,
            "wallet_mode" to walletMode.key,
            "trigger" to trigger.key,
            "outcome" to outcome.key,
            "is_first_open" to isFirstOpen,
            "network_type" to networkType.key,
            "is_low_power" to isLowPower,
            "thermal_state" to thermalState.key
        )
        errorKind?.let { props["error_kind"] = it.key }
        cachedMs?.let { props["cached_ms"] = it }
        freshMs?.let { props["fresh_ms"] = it }
        screenCreatedMs?.let { props["screen_created_ms"] = it }
        screenWillShowMs?.let { props["screen_will_show_ms"] = it }
        screenShownMs?.let { props["screen_shown_ms"] = it }
        requestMs?.let { props["request_ms"] = it }
        marks?.let { props["marks"] = it }
        pausedMs?.let { props["paused_ms"] = it }
        hadPasscode?.let { props["had_passcode"] = it }
        trackEvent("screen_content_rendered", props)
    }
}
