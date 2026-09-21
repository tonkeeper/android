package com.tonapps.portfolio.analytics

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events

object StakingAnalytics {

    data class Props(
        val jettonSymbol: String,
        val providerName: String,
        val providerDomain: String,
    )

    private val events: Events.StakingNative
        get() = AnalyticsHelper.Default.events.stakingNative

    fun open(from: String) {
        events.stakingOpen(from)
    }

    fun stakeInput(from: String, props: Props) {
        events.stakingPlusInput(
            from = from,
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun stakeConfirm(props: Props) {
        events.stakingPlusConfirm(
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun stakeSuccess(props: Props) {
        events.stakingPlusSuccess(
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun unstakeInput(from: String, props: Props) {
        events.stakingMinusInput(
            from = from,
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun unstakeConfirm(props: Props) {
        events.stakingMinusConfirm(
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun unstakeSuccess(props: Props) {
        events.stakingMinusSuccess(
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }
}
