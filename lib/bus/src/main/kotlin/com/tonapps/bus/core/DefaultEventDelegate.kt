package com.tonapps.bus.core

import android.net.Uri
import androidx.annotation.UiThread
import com.tonapps.blockchain.ton.TonAddressTags
import com.tonapps.bus.core.contract.EventDelegate
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.extensions.hostOrNull
import com.tonapps.extensions.toUriOrNull

class DefaultEventDelegate(
    private val eventExecutor: EventExecutor,
) : EventDelegate {

    private val regexPrivateData: Regex by lazy {
        Regex("[a-f0-9]{64}|0:[a-f0-9]{64}")
    }

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    private fun removePrivateDataFromUrl(url: String): String {
        return url.replace(regexPrivateData, "X")
    }

    private fun getAddressType(address: String): String {
        if (address.startsWith("0:")) {
            return "raw"
        }

        val tags = TonAddressTags.of(address)
        return if (tags.isBounceable) "bounce" else "non-bounce"
    }

    @UiThread
    override fun tcRequest(url: String) {
        val props = hashMapOf(
            "dapp_url" to url
        )
        trackEvent("tc_request", props)
    }

    @UiThread
    override fun swapOpen(uri: Uri, native: Boolean) {
        val props = hashMapOf(
            "provider_name" to (uri.host ?: "unknown"),
            "provider_domain" to uri
        )
        if (native) {
            props["type"] = "native"
        } else {
            props["type"] = "old"
        }
        trackEvent("swap_open", props)
    }

    @UiThread
    override fun swapClick(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        native: Boolean,
        providerName: String,
    ) {
        val props = hashMapOf(
            "jetton_symbol_from" to jettonSymbolFrom,
            "jetton_symbol_to" to jettonSymbolTo,
            "type" to if (native) "native" else "old",
            "provider_name" to providerName
        )
        trackEvent("swap_click", props)
    }

    @UiThread
    override fun swapConfirm(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        providerName: String,
        providerUrl: String,
        native: Boolean
    ) {
        val props = hashMapOf(
            "jetton_symbol_from" to jettonSymbolFrom,
            "jetton_symbol_to" to jettonSymbolTo,
            "type" to if (native) "native" else "old",
            "provider_name" to providerName,
            "provider_domain" to (providerUrl.toUriOrNull()?.hostOrNull ?: providerUrl)
        )
        trackEvent("swap_confirm", props)
    }

    @UiThread
    override fun swapSuccess(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        providerName: String,
        providerUrl: String,
        native: Boolean
    ) {
        val props = hashMapOf(
            "jetton_symbol_from" to jettonSymbolFrom,
            "jetton_symbol_to" to jettonSymbolTo,
            "type" to if (native) "native" else "old",
            "provider_name" to providerName,
            "provider_domain" to (providerUrl.toUriOrNull()?.hostOrNull ?: providerUrl)
        )
        trackEvent("swap_success", props)
    }

    @UiThread
    override fun dappSharingCopy(name: String, from: String, url: String) {
        val props = hashMapOf(
            "name" to name,
            "from" to from,
            "url" to url
        )
        trackEvent("dapp_sharing_copy", props)
    }

    @UiThread
    override fun tcConnect(url: String, pushEnabled: Boolean) {
        val props = hashMapOf(
            "dapp_url" to url,
            "allow_notifications" to pushEnabled
        )
        trackEvent("tc_connect", props)
    }

    @UiThread
    override fun tcViewConfirm(url: String, address: String) {
        val props = hashMapOf(
            "dapp_url" to url,
            "address_type" to getAddressType(address)
        )
        trackEvent("tc_view_confirm", props)
    }

    @UiThread
    override fun tcSendSuccess(url: String, address: String, feePaid: String) {
        val props = hashMapOf(
            "dapp_url" to url,
            "address_type" to getAddressType(address),
            "network_fee_paid" to feePaid
        )
        trackEvent("tc_send_success", props)
    }

    @UiThread
    override fun openRefDeeplink(deeplink: String) {
        val props = hashMapOf(
            "deeplink" to deeplink
        )
        trackEvent("ads_deeplink", props)
    }

    @UiThread
    override fun batterySuccess(
        type: String,
        promo: String,
        token: String,
        size: String?
    ) {
        trackEvent(
            "battery_success", hashMapOf(
                "type" to type,
                "promo" to promo,
                "jetton" to token,
                "size" to (size ?: "null")
            )
        )
    }

    @UiThread
    override fun onRampOpen(source: String) {
        trackEvent(
            "onramp_open", hashMapOf(
                "from" to source,
            )
        )
    }

    @UiThread
    override fun onRampEnterAmount(
        type: String,
        sellAsset: String,
        buyAsset: String,
        countryCode: String
    ) {
        val props = hashMapOf(
            "type" to type,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset,
            "country_code" to countryCode
        )
        trackEvent("onramp_enter_amount", props)
    }

    @UiThread
    override fun onRampOpenWebview(
        type: String,
        sellAsset: String,
        buyAsset: String,
        countryCode: String,
        paymentMethod: String?,
        providerName: String,
        providerDomain: String
    ) {

        fun fixPaymentMethodName(value: String): String {
            return when (value) {
                "card" -> "Credit Card"
                "google_pay" -> "Google Pay"
                "paypal" -> "PayPal"
                "revolut" -> "Revolut"
                else -> value
            }
        }

        val props = hashMapOf(
            "type" to type,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset,
            "country_code" to countryCode,
            "payment_method" to (paymentMethod?.let(::fixPaymentMethodName) ?: "unknown"),
            "provider_name" to providerName,
            "provider_domain" to providerDomain
        )
        trackEvent("onramp_continue_to_provider", props)
    }

    @UiThread
    override fun onRampClick(
        type: String,
        placement: String,
        location: String,
        name: String,
        url: String
    ) {
        trackEvent(
            "onramp_click", hashMapOf(
                "type" to type,
                "placement" to placement,
                "location" to location,
                "name" to name,
                "url" to url
            )
        )
    }

    @UiThread
    override fun trackPushClick(pushId: String, payload: String) {
        trackEvent(
            "push_click", hashMapOf(
                "push_id" to pushId,
                "payload" to removePrivateDataFromUrl(payload)
            )
        )
    }

    @UiThread
    override fun trackStoryClick(
        storiesId: String,
        title: String,
        type: String,
        payload: String,
        index: Int
    ) {
        trackEvent(
            "story_click", hashMapOf(
                "story_id" to storiesId,
                "button_title" to title,
                "button_type" to type,
                "button_payload" to payload,
                "page_number" to index + 1
            )
        )
    }

    @UiThread
    override fun trackStoryView(storiesId: String, index: Int) {
        trackEvent(
            "story_page_view", hashMapOf(
                "story_id" to storiesId,
                "page_number" to index
            )
        )
    }

    @UiThread
    override fun trackStoryOpen(storiesId: String, from: String) {
        trackEvent(
            "story_open", hashMapOf(
                "story_id" to storiesId,
                "from" to from
            )
        )
    }

    @UiThread
    override fun dappClick(
        url: String,
        name: String,
        source: String,
        country: String,
    ) {
        trackEvent(
            "click_dapp", hashMapOf(
                "url" to url,
                "name" to name,
                "from" to source,
                "location" to country
            )
        )
    }
}
