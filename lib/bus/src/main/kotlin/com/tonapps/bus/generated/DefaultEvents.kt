package com.tonapps.bus.generated

import androidx.annotation.UiThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events.TransactionSent.*
import com.tonapps.bus.generated.Events.BatteryNative.*
import com.tonapps.bus.generated.Events.DappBrowser.*
import com.tonapps.bus.generated.Events.OnrampsNative.*
import com.tonapps.bus.generated.Events.SendNative.*
import com.tonapps.bus.generated.Events.SwapsNative.*
import com.tonapps.bus.generated.Events.TonConnect.*

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class DefaultEvents(
    private val eventExecutor: EventExecutor,
) {

    val transactionSent = TransactionSentImpl(eventExecutor)
    val batteryNative = BatteryNativeImpl(eventExecutor)
    val dappBrowser = DappBrowserImpl(eventExecutor)
    val installApp = InstallAppImpl(eventExecutor)
    val launchApp = LaunchAppImpl(eventExecutor)
    val onrampsNative = OnrampsNativeImpl(eventExecutor)
    val sendNative = SendNativeImpl(eventExecutor)
    val stakingNative = StakingNativeImpl(eventExecutor)
    val swapsNative = SwapsNativeImpl(eventExecutor)
    val tonConnect = TonConnectImpl(eventExecutor)

    class TransactionSentImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.TransactionSent {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun transactionSent(eventType: TransactionSentEventType) {
            trackEvent("transaction_sent", hashMapOf("event_type" to eventType.key))
        }
    }

    class BatteryNativeImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.BatteryNative {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun batteryOpen(from: BatteryNativeFrom) {
            trackEvent("battery_open", hashMapOf("from" to from.key))
        }

        @UiThread
        override fun batterySelect(
            from: BatteryNativeFrom,
            type: BatteryNativeType,
            size: BatteryNativeSize,
            promo: String?,
            jetton: String?
        ) {
            val props = hashMapOf<String, Any>(
                "from" to from.key,
                "type" to type.key,
                "size" to size.key
            )
            promo?.let { props["promo"] = it }
            jetton?.let { props["jetton"] = it }
            trackEvent("battery_select", props)
        }

        @UiThread
        override fun batterySuccess(
            from: BatteryNativeFrom,
            type: BatteryNativeType,
            size: BatteryNativeSize,
            promo: String?,
            jetton: String?
        ) {
            val props = hashMapOf<String, Any>(
                "from" to from.key,
                "type" to type.key,
                "size" to size.key
            )
            promo?.let { props["promo"] = it }
            jetton?.let { props["jetton"] = it }
            trackEvent("battery_success", props)
        }
    }

    class DappBrowserImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.DappBrowser {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun dappBrowserOpen(
            from: DappBrowserOpenFrom,
            type: DappBrowserType,
            location: String
        ) {
            val props = hashMapOf(
                "from" to from.key,
                "type" to type.key,
                "location" to location
            )
            trackEvent("dapp_browser_open", props)
        }

        @UiThread
        override fun dappPin(url: String, location: String) {
            trackEvent("dapp_pin", hashMapOf("url" to url, "location" to location))
        }

        @UiThread
        override fun dappUnpin(url: String, location: String) {
            trackEvent("dapp_unpin", hashMapOf("url" to url, "location" to location))
        }

        @UiThread
        override fun dappSharingCopy(url: String, from: DappSharingCopyFrom) {
            trackEvent("dapp_sharing_copy", hashMapOf("url" to url, "from" to from.key))
        }

        @UiThread
        override fun dappAppOpen(
            from: DappAppOpenFrom,
            url: String,
            appId: String,
            bannerId: String?,
            location: String
        ) {
            val props = hashMapOf<String, Any>(
                "from" to from.key,
                "url" to url,
                "app_id" to appId,
                "location" to location
            )
            bannerId?.let { props["banner_id"] = it }
            trackEvent("dapp_app_open", props)
        }

        @UiThread
        override fun dappBrowserSearchOpen(url: String, location: String) {
            trackEvent("dapp_browser_search_open", hashMapOf("url" to url, "location" to location))
        }

        @UiThread
        override fun dappBrowserSearchClick(url: String, location: String) {
            trackEvent("dapp_browser_search_click", hashMapOf("url" to url, "location" to location))
        }
    }

    class InstallAppImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.InstallApp {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun installApp(referrer: String?, deeplink: String?) {
            val props = mutableMapOf<String, Any>()
            referrer?.let { props["referrer"] = it }
            deeplink?.let { props["deeplink"] = it }
            trackEvent("install_app", props)
        }
    }

    class LaunchAppImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.LaunchApp {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun launchApp() {
            trackEvent("launch_app", emptyMap())
        }
    }

    class OnrampsNativeImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.OnrampsNative {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun onrampOpen(from: String) {
            trackEvent("onramp_open", hashMapOf("from" to from))
        }

        @UiThread
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

        @UiThread
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

        @UiThread
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

        @UiThread
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

    class SendNativeImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.SendNative {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun sendOpen(from: SendNativeFrom) {
            trackEvent("send_open", hashMapOf("from" to from.key))
        }

        @UiThread
        override fun sendClick(
            from: SendNativeFrom,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double
        ) {
            val props = hashMapOf(
                "from" to from.key,
                "asset_network" to assetNetwork,
                "token_symbol" to tokenSymbol,
                "amount" to amount
            )
            trackEvent("send_click", props)
        }

        @UiThread
        override fun sendConfirm(
            from: SendNativeFrom,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double,
            feePaidIn: SendNativeFeePaidIn,
            appId: String?
        ) {
            val props = hashMapOf<String, Any>(
                "from" to from.key,
                "asset_network" to assetNetwork,
                "token_symbol" to tokenSymbol,
                "amount" to amount,
                "fee_paid_in" to feePaidIn.key
            )
            appId?.let { props["app_id"] = it }
            trackEvent("send_confirm", props)
        }

        @UiThread
        override fun sendSuccess(
            from: SendNativeFrom,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double,
            feePaidIn: SendNativeFeePaidIn,
            transactionId: String,
            appId: String?
        ) {
            val props = hashMapOf<String, Any>(
                "from" to from.key,
                "asset_network" to assetNetwork,
                "token_symbol" to tokenSymbol,
                "amount" to amount,
                "fee_paid_in" to feePaidIn.key,
                "transaction_id" to transactionId
            )
            appId?.let { props["app_id"] = it }
            trackEvent("send_success", props)
        }

        @UiThread
        override fun sendFailed(
            from: SendNativeFrom,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double,
            feePaidIn: SendNativeFeePaidIn,
            errorCode: Int,
            errorMessage: String,
            appId: String?
        ) {
            val props = hashMapOf<String, Any>(
                "from" to from.key,
                "asset_network" to assetNetwork,
                "token_symbol" to tokenSymbol,
                "amount" to amount,
                "fee_paid_in" to feePaidIn.key,
                "error_code" to errorCode,
                "error_message" to errorMessage
            )
            appId?.let { props["app_id"] = it }
            trackEvent("send_failed", props)
        }
    }

    class StakingNativeImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.StakingNative {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun stakingOpen(from: String) {
            trackEvent("staking_open", hashMapOf("from" to from))
        }

        @UiThread
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

        @UiThread
        override fun stakingPlusConfirm(
            jettonSymbol: String,
            providerName: String,
            providerDomain: String
        ) {
            val props = hashMapOf(
                "jetton_symbol" to jettonSymbol,
                "provider_name" to providerName,
                "provider_domain" to providerDomain
            )
            trackEvent("staking_plus_confirm", props)
        }

        @UiThread
        override fun stakingPlusSuccess(
            jettonSymbol: String,
            providerName: String,
            providerDomain: String
        ) {
            val props = hashMapOf(
                "jetton_symbol" to jettonSymbol,
                "provider_name" to providerName,
                "provider_domain" to providerDomain
            )
            trackEvent("staking_plus_success", props)
        }

        @UiThread
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

        @UiThread
        override fun stakingMinusConfirm(
            jettonSymbol: String,
            providerName: String,
            providerDomain: String
        ) {
            val props = hashMapOf(
                "jetton_symbol" to jettonSymbol,
                "provider_name" to providerName,
                "provider_domain" to providerDomain
            )
            trackEvent("staking_minus_confirm", props)
        }

        @UiThread
        override fun stakingMinusSuccess(
            jettonSymbol: String,
            providerName: String,
            providerDomain: String
        ) {
            val props = hashMapOf(
                "jetton_symbol" to jettonSymbol,
                "provider_name" to providerName,
                "provider_domain" to providerDomain
            )
            trackEvent("staking_minus_success", props)
        }
    }

    class SwapsNativeImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.SwapsNative {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun swapOpen(type: SwapsNativeType) {
            trackEvent("swap_open", hashMapOf("type" to type.key))
        }

        @UiThread
        override fun swapClick(
            type: SwapsNativeType,
            jettonSymbolFrom: String,
            jettonSymbolTo: String
        ) {
            val props = hashMapOf(
                "type" to type.key,
                "jetton_symbol_from" to jettonSymbolFrom,
                "jetton_symbol_to" to jettonSymbolTo
            )
            trackEvent("swap_click", props)
        }

        @UiThread
        override fun swapConfirm(
            type: SwapsNativeType,
            feePaidIn: SwapsNativeFeePaidIn,
            jettonSymbolFrom: String,
            jettonSymbolTo: String,
            providerName: String
        ) {
            val props = hashMapOf(
                "type" to type.key,
                "fee_paid_in" to feePaidIn.key,
                "jetton_symbol_from" to jettonSymbolFrom,
                "jetton_symbol_to" to jettonSymbolTo,
                "provider_name" to providerName
            )
            trackEvent("swap_confirm", props)
        }

        @UiThread
        override fun swapFailed(
            type: SwapsNativeType,
            errorMessage: String,
            feePaidIn: SwapsNativeFeePaidIn,
            jettonSymbolFrom: String,
            jettonSymbolTo: String,
            providerName: String
        ) {
            val props = hashMapOf(
                "type" to type.key,
                "error_message" to errorMessage,
                "fee_paid_in" to feePaidIn.key,
                "jetton_symbol_from" to jettonSymbolFrom,
                "jetton_symbol_to" to jettonSymbolTo,
                "provider_name" to providerName
            )
            trackEvent("swap_failed", props)
        }

        @UiThread
        override fun swapSuccess(
            type: SwapsNativeType,
            feePaidIn: SwapsNativeFeePaidIn,
            jettonSymbolFrom: String,
            jettonSymbolTo: String,
            providerName: String
        ) {
            val props = hashMapOf(
                "type" to type.key,
                "fee_paid_in" to feePaidIn.key,
                "jetton_symbol_from" to jettonSymbolFrom,
                "jetton_symbol_to" to jettonSymbolTo,
                "provider_name" to providerName
            )
            trackEvent("swap_success", props)
        }
    }

    class TonConnectImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.TonConnect {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        @UiThread
        override fun tcRequest(dappUrl: String) {
            trackEvent("tc_request", hashMapOf("dapp_url" to dappUrl))
        }

        @UiThread
        override fun tcConnect(dappUrl: String, allowNotifications: Boolean) {
            trackEvent(
                "tc_connect",
                hashMapOf("dapp_url" to dappUrl, "allow_notifications" to allowNotifications)
            )
        }

        @UiThread
        override fun tcViewConfirm(dappUrl: String, addressType: TonConnectAddressType) {
            trackEvent(
                "tc_view_confirm",
                hashMapOf("dapp_url" to dappUrl, "address_type" to addressType.key)
            )
        }

        @UiThread
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

        @UiThread
        override fun tcSignDataSuccess(dappUrl: String, payloadType: TonConnectPayloadType) {
            trackEvent(
                "tc_sign_data_success",
                hashMapOf("dapp_url" to dappUrl, "payload_type" to payloadType.key)
            )
        }
    }
}
