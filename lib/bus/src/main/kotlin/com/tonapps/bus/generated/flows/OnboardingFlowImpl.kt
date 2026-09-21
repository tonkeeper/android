package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class OnboardingFlowImpl(
    private val eventExecutor: EventExecutor,
) : Events.OnboardingFlow {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * onboarding_view_welcome
     *
     * User sees the Welcome screen ("Create a new wallet or add an existing one") — shown on app start while no wallets exist. Top of the onboarding funnel: conversion from app launch into onboarding is measured against this event.

     */
    @AnyThread
    override fun onboardingViewWelcome() {
        trackEvent("onboarding_view_welcome", emptyMap())
    }

    /**
     * onboarding_passcode_created
     *
     * User successfully set a passcode while creating a wallet during onboarding — entered it twice and both entries matched

     */
    @AnyThread
    override fun onboardingPasscodeCreated() {
        trackEvent("onboarding_passcode_created", emptyMap())
    }

    /**
     * onboarding_passcode_mismatch
     *
     * While setting up a passcode during onboarding, the user's confirmation entry did not match the first entry, so the passcode was not created and the user is asked to try again. This is a data-entry error inside the create-passcode step — unrelated to passcode_lockout, which is a brute-force lockout after repeated failed attempts on an existing passcode.

     */
    @AnyThread
    override fun onboardingPasscodeMismatch() {
        trackEvent("onboarding_passcode_mismatch", emptyMap())
    }

    /**
     * onboarding_view_customize
     *
     * User sees the Customize Wallet screen (name, color, icon) at the end of onboarding wallet creation. Without this event a drop-off on Customize is only visible as a missing wallet_create_success.

     */
    @AnyThread
    override fun onboardingViewCustomize() {
        trackEvent("onboarding_view_customize", emptyMap())
    }

    /**
     * onboarding_click_customize_continue
     *
     * User tapped Continue on the Customize Wallet screen during onboarding; the wallet is then created and wallet_create_success follows

     */
    @AnyThread
    override fun onboardingClickCustomizeContinue() {
        trackEvent("onboarding_click_customize_continue", emptyMap())
    }
}
