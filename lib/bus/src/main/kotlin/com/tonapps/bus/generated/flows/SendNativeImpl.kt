package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.SendNative.SendNativeFeeAsset
import com.tonapps.bus.generated.Events.SendNative.SendNativeFrom

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class SendNativeImpl(
    private val eventExecutor: EventExecutor,
) : Events.SendNative {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * send_open
     *
     * User opened the send screen
     */
    @AnyThread
    override fun sendOpen(from: SendNativeFrom) {
        trackEvent("send_open", hashMapOf("from" to from.key))
    }

    /**
     * send_click
     *
     * User clicked 'Continue' after entering recipient and amount
     */
    @AnyThread
    override fun sendClick(from: SendNativeFrom, asset: String, amount: Double) {
        val props = hashMapOf(
            "from" to from.key,
            "asset" to asset,
            "amount" to amount
        )
        trackEvent("send_click", props)
    }

    /**
     * send_confirm
     *
     * User is on the confirmation screen and slides/clicks "Confirm and Send"
     */
    @AnyThread
    override fun sendConfirm(
        from: SendNativeFrom,
        asset: String,
        amount: Double,
        feeAsset: SendNativeFeeAsset,
        appId: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "asset" to asset,
            "amount" to amount,
            "fee_asset" to feeAsset.key
        )
        appId?.let { props["app_id"] = it }
        trackEvent("send_confirm", props)
    }

    /**
     * send_success
     *
     * Send transaction completed successfully
     */
    @AnyThread
    override fun sendSuccess(
        from: SendNativeFrom,
        asset: String,
        amount: Double,
        feeAsset: SendNativeFeeAsset,
        appId: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "asset" to asset,
            "amount" to amount,
            "fee_asset" to feeAsset.key
        )
        appId?.let { props["app_id"] = it }
        trackEvent("send_success", props)
    }

    /**
     * send_failed
     *
     * Send transaction failed
     */
    @AnyThread
    override fun sendFailed(
        from: SendNativeFrom,
        asset: String,
        amount: Double,
        feeAsset: SendNativeFeeAsset,
        errorCode: Int,
        errorMessage: String,
        appId: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "asset" to asset,
            "amount" to amount,
            "fee_asset" to feeAsset.key,
            "error_code" to errorCode,
            "error_message" to errorMessage
        )
        appId?.let { props["app_id"] = it }
        trackEvent("send_failed", props)
    }
}
