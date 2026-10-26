package com.tonapps.portfolio.analytics

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events

object StakingAnalytics {

    data class Props(
        val jettonSymbol: String,
        val providerName: String,
        val providerDomain: String,
    )

    private fun events(utm: UtmParameters): Events.StakingNative {
        return AnalyticsHelper.Default.eventsTaggedWith(utm).stakingNative
    }

    fun open(from: String, utm: UtmParameters) {
        events(utm).stakingOpen(from)
    }

    fun stakeInput(from: String, props: Props, utm: UtmParameters) {
        events(utm).stakingPlusInput(
            from = from,
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun stakeConfirm(props: Props, utm: UtmParameters) {
        events(utm).stakingPlusConfirm(
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun stakeSuccess(props: Props, utm: UtmParameters) {
        events(utm).stakingPlusSuccess(
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun unstakeInput(from: String, props: Props, utm: UtmParameters) {
        events(utm).stakingMinusInput(
            from = from,
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun unstakeConfirm(props: Props, utm: UtmParameters) {
        events(utm).stakingMinusConfirm(
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }

    fun unstakeSuccess(props: Props, utm: UtmParameters) {
        events(utm).stakingMinusSuccess(
            jettonSymbol = props.jettonSymbol,
            providerName = props.providerName,
            providerDomain = props.providerDomain,
        )
    }
}
