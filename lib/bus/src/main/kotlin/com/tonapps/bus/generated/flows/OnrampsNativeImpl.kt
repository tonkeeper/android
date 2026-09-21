package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.OnrampsNative.OnrampsNativeType

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class OnrampsNativeImpl(
    private val eventExecutor: EventExecutor,
) : Events.OnrampsNative {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * onramp_open
     *
     * User on WALLET screen clicked BUY&SELL action button
     */
    @AnyThread
    override fun onrampOpen(from: String) {
        trackEvent("onramp_open", hashMapOf("from" to from))
    }

    /**
     * onramp_enter_amount
     *
     * User clicked 'continue' after entering buy/sell amount
     */
    @AnyThread
    override fun onrampEnterAmount(
        txId: String?,
        type: OnrampsNativeType,
        sellAssetNetwork: String,
        sellAssetSymbol: String,
        sellAmount: Double,
        buyAssetNetwork: String,
        buyAssetSymbol: String,
        buyAmount: Double,
        countryCode: String?
    ) {
        val props = hashMapOf<String, Any>(
            "type" to type.key,
            "sell_asset_network" to sellAssetNetwork,
            "sell_asset_symbol" to sellAssetSymbol,
            "sell_amount" to sellAmount,
            "buy_asset_network" to buyAssetNetwork,
            "buy_asset_symbol" to buyAssetSymbol,
            "buy_amount" to buyAmount
        )
        txId?.let { props["tx_id"] = it }
        countryCode?.let { props["country_code"] = it }
        trackEvent("onramp_enter_amount", props)
    }

    /**
     * onramp_continue_to_provider
     *
     * User clicked 'continue' after choosing the payment method and provider
     */
    @AnyThread
    override fun onrampContinueToProvider(
        txId: String?,
        type: OnrampsNativeType,
        sellAssetNetwork: String,
        sellAssetSymbol: String,
        sellAmount: Double,
        buyAssetNetwork: String,
        buyAssetSymbol: String,
        buyAmount: Double,
        countryCode: String?,
        paymentMethod: String,
        providerName: String,
        providerDomain: String
    ) {
        val props = hashMapOf<String, Any>(
            "type" to type.key,
            "sell_asset_network" to sellAssetNetwork,
            "sell_asset_symbol" to sellAssetSymbol,
            "sell_amount" to sellAmount,
            "buy_asset_network" to buyAssetNetwork,
            "buy_asset_symbol" to buyAssetSymbol,
            "buy_amount" to buyAmount,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        txId?.let { props["tx_id"] = it }
        countryCode?.let { props["country_code"] = it }
        trackEvent("onramp_continue_to_provider", props)
    }

    /**
     * onramp_success
     *
     * Onramp transaction completed successfully
     */
    @AnyThread
    override fun onrampSuccess(
        txId: String?,
        type: OnrampsNativeType,
        sellAssetNetwork: String,
        sellAssetSymbol: String,
        sellAmount: Double,
        buyAssetNetwork: String,
        buyAssetSymbol: String,
        buyAmount: Double,
        countryCode: String?,
        paymentMethod: String,
        providerName: String,
        providerDomain: String
    ) {
        val props = hashMapOf<String, Any>(
            "type" to type.key,
            "sell_asset_network" to sellAssetNetwork,
            "sell_asset_symbol" to sellAssetSymbol,
            "sell_amount" to sellAmount,
            "buy_asset_network" to buyAssetNetwork,
            "buy_asset_symbol" to buyAssetSymbol,
            "buy_amount" to buyAmount,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        txId?.let { props["tx_id"] = it }
        countryCode?.let { props["country_code"] = it }
        trackEvent("onramp_success", props)
    }

    /**
     * onramp_fail
     *
     * Onramp transaction failed
     */
    @AnyThread
    override fun onrampFail(
        txId: String?,
        type: OnrampsNativeType,
        sellAssetNetwork: String,
        sellAssetSymbol: String,
        sellAmount: Double,
        buyAssetNetwork: String,
        buyAssetSymbol: String,
        buyAmount: Double,
        countryCode: String?,
        paymentMethod: String,
        providerName: String,
        providerDomain: String,
        errorCode: String?,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>(
            "type" to type.key,
            "sell_asset_network" to sellAssetNetwork,
            "sell_asset_symbol" to sellAssetSymbol,
            "sell_amount" to sellAmount,
            "buy_asset_network" to buyAssetNetwork,
            "buy_asset_symbol" to buyAssetSymbol,
            "buy_amount" to buyAmount,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        txId?.let { props["tx_id"] = it }
        countryCode?.let { props["country_code"] = it }
        errorCode?.let { props["error_code"] = it }
        errorMessage?.let { props["error_message"] = it }
        trackEvent("onramp_fail", props)
    }
}
