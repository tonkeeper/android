package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.TonConnect.TonConnectAddressType
import com.tonapps.bus.generated.Events.TonConnect.TonConnectNetworkFeePaid
import com.tonapps.bus.generated.Events.TonConnect.TonConnectPayloadType

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class TonConnectImpl(
    private val eventExecutor: EventExecutor,
) : Events.TonConnect {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /** tc_request */
    @AnyThread
    override fun tcRequest(dappUrl: String) {
        trackEvent("tc_request", hashMapOf("dapp_url" to dappUrl))
    }

    /** tc_connect */
    @AnyThread
    override fun tcConnect(dappUrl: String, allowNotifications: Boolean) {
        trackEvent("tc_connect", hashMapOf("dapp_url" to dappUrl, "allow_notifications" to allowNotifications))
    }

    /** tc_view_confirm */
    @AnyThread
    override fun tcViewConfirm(dappUrl: String, addressType: TonConnectAddressType) {
        trackEvent("tc_view_confirm", hashMapOf("dapp_url" to dappUrl, "address_type" to addressType.key))
    }

    /** tc_send_success */
    @AnyThread
    override fun tcSendSuccess(
        dappUrl: String,
        addressType: TonConnectAddressType,
        networkFeePaid: TonConnectNetworkFeePaid
    ) {
        val props = hashMapOf(
            "dapp_url" to dappUrl,
            "address_type" to addressType.key,
            "network_fee_paid" to networkFeePaid.key
        )
        trackEvent("tc_send_success", props)
    }

    /** tc_sign_data_success */
    @AnyThread
    override fun tcSignDataSuccess(dappUrl: String, payloadType: TonConnectPayloadType) {
        trackEvent("tc_sign_data_success", hashMapOf("dapp_url" to dappUrl, "payload_type" to payloadType.key))
    }
}
