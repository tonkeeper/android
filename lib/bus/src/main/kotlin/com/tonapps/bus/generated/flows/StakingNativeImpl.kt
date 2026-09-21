package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class StakingNativeImpl(
    private val eventExecutor: EventExecutor,
) : Events.StakingNative {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * staking_open
     *
     * User opened staking page
     */
    @AnyThread
    override fun stakingOpen(from: String) {
        trackEvent("staking_open", hashMapOf("from" to from))
    }

    /**
     * staking_plus_input
     *
     * User is presented with the staking input field
     */
    @AnyThread
    override fun stakingPlusInput(
        from: String,
        jettonSymbol: String,
        providerName: String,
        providerDomain: String
    ) {
        val props = hashMapOf(
            "from" to from,
            "jetton_symbol" to jettonSymbol,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        trackEvent("staking_plus_input", props)
    }

    /**
     * staking_plus_confirm
     *
     * User is presented with the staking confirmation slider
     */
    @AnyThread
    override fun stakingPlusConfirm(jettonSymbol: String, providerName: String, providerDomain: String) {
        val props = hashMapOf(
            "jetton_symbol" to jettonSymbol,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        trackEvent("staking_plus_confirm", props)
    }

    /**
     * staking_plus_success
     *
     * Staking successful
     */
    @AnyThread
    override fun stakingPlusSuccess(jettonSymbol: String, providerName: String, providerDomain: String) {
        val props = hashMapOf(
            "jetton_symbol" to jettonSymbol,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        trackEvent("staking_plus_success", props)
    }

    /**
     * staking_minus_input
     *
     * User is presented with the unstaking input field
     */
    @AnyThread
    override fun stakingMinusInput(
        from: String,
        jettonSymbol: String,
        providerName: String,
        providerDomain: String
    ) {
        val props = hashMapOf(
            "from" to from,
            "jetton_symbol" to jettonSymbol,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        trackEvent("staking_minus_input", props)
    }

    /**
     * staking_minus_confirm
     *
     * User is presented with the unstaking confirmation slider
     */
    @AnyThread
    override fun stakingMinusConfirm(jettonSymbol: String, providerName: String, providerDomain: String) {
        val props = hashMapOf(
            "jetton_symbol" to jettonSymbol,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        trackEvent("staking_minus_confirm", props)
    }

    /**
     * staking_minus_success
     *
     * Unstaking successful
     */
    @AnyThread
    override fun stakingMinusSuccess(jettonSymbol: String, providerName: String, providerDomain: String) {
        val props = hashMapOf(
            "jetton_symbol" to jettonSymbol,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        trackEvent("staking_minus_success", props)
    }
}
