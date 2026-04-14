package com.tonapps.bus.generated

import androidx.annotation.UiThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeSize
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeType
import com.tonapps.bus.generated.Events.DappBrowser.DappAppOpenFrom
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserOpenFrom
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserType
import com.tonapps.bus.generated.Events.DappBrowser.DappSharingCopyFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowBuyAsset
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowSellAsset
import com.tonapps.bus.generated.Events.OnrampsNative.OnrampsNativeType
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsFlow
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOutcome
import com.tonapps.bus.generated.Events.SendNative.SendNativeFeePaidIn
import com.tonapps.bus.generated.Events.SendNative.SendNativeFrom
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeFeePaidIn
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeType
import com.tonapps.bus.generated.Events.TonConnect.TonConnectAddressType
import com.tonapps.bus.generated.Events.TonConnect.TonConnectNetworkFeePaid
import com.tonapps.bus.generated.Events.TonConnect.TonConnectPayloadType
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentEventType
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowBuyAsset
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFeePaidIn
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowSellAsset

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
    val depositFlow = DepositFlowImpl(eventExecutor)
    val installApp = InstallAppImpl(eventExecutor)
    val launchApp = LaunchAppImpl(eventExecutor)
    val onrampsNative = OnrampsNativeImpl(eventExecutor)
    val redOperations = RedOperationsImpl(eventExecutor)
    val sendNative = SendNativeImpl(eventExecutor)
    val stakingNative = StakingNativeImpl(eventExecutor)
    val swapsNative = SwapsNativeImpl(eventExecutor)
    val tonConnect = TonConnectImpl(eventExecutor)
    val withdrawFlow = WithdrawFlowImpl(eventExecutor)

    class TransactionSentImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.TransactionSent {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        /**
         * transaction_sent
         *
         * Sent when any blockchain transaction is submitted
         */
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

        /**
         * battery_open
         *
         * User opens the Battery purchase screen
         */
        @UiThread
        override fun batteryOpen(from: BatteryNativeFrom) {
            trackEvent("battery_open", hashMapOf("from" to from.key))
        }

        /**
         * battery_select
         *
         * User selected a battery pack/amount and clicked continue
         */
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

        /**
         * battery_success
         *
         * User successfully completed a battery purchase
         */
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

        /**
         * dapp_browser_open
         *
         * Triggered when user opens the Browser/Discover section
         */
        @UiThread
        override fun dappBrowserOpen(from: DappBrowserOpenFrom, type: DappBrowserType, location: String) {
            val props = hashMapOf(
                "from" to from.key,
                "type" to type.key,
                "location" to location
            )
            trackEvent("dapp_browser_open", props)
        }

        /** dapp_pin */
        @UiThread
        override fun dappPin(url: String, location: String) {
            trackEvent("dapp_pin", hashMapOf("url" to url, "location" to location))
        }

        /** dapp_unpin */
        @UiThread
        override fun dappUnpin(url: String, location: String) {
            trackEvent("dapp_unpin", hashMapOf("url" to url, "location" to location))
        }

        /** dapp_sharing_copy */
        @UiThread
        override fun dappSharingCopy(url: String, from: DappSharingCopyFrom) {
            trackEvent("dapp_sharing_copy", hashMapOf("url" to url, "from" to from.key))
        }

        /**
         * dapp_app_open
         *
         * Triggered when user opens a dapp
         */
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

        /**
         * dapp_browser_search_open
         *
         * Triggered when user makes a search request in the browser
         */
        @UiThread
        override fun dappBrowserSearchOpen(url: String, location: String) {
            trackEvent("dapp_browser_search_open", hashMapOf("url" to url, "location" to location))
        }

        /**
         * dapp_browser_search_click
         *
         * Triggered when user clicks somewhere from search in a browser session
         */
        @UiThread
        override fun dappBrowserSearchClick(url: String, location: String) {
            trackEvent("dapp_browser_search_click", hashMapOf("url" to url, "location" to location))
        }
    }

    class DepositFlowImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.DepositFlow {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        /**
         * deposit_open
         *
         * User entered the Deposit screen
         */
        @UiThread
        override fun depositOpen(from: DepositFlowFrom) {
            trackEvent("deposit_open", hashMapOf("from" to from.key))
        }

        /**
         * deposit_click_buy
         *
         * User tapped a buy option on the Deposit screen (Figma: Deposit with Cash or Crypto)
         */
        @UiThread
        override fun depositClickBuy(buyAsset: DepositFlowBuyAsset) {
            trackEvent("deposit_click_buy", hashMapOf("buy_asset" to buyAsset.key))
        }

        /**
         * deposit_view_p2p
         *
         * User sees the P2P market screen (Figma: Wallet_flow)
         */
        @UiThread
        override fun depositViewP2p(buyAsset: DepositFlowBuyAsset, sellAsset: DepositFlowSellAsset) {
            trackEvent("deposit_view_p2p", hashMapOf("buy_asset" to buyAsset.key, "sell_asset" to sellAsset.key))
        }

        /**
         * deposit_view_onramp_insert_amount
         *
         * User sees the onramp amount entry screen (Figma: Insert_amount). If the user switches to another provider, this event fires again with the new provider_name.

         */
        @UiThread
        override fun depositViewOnrampInsertAmount(buyAsset: DepositFlowBuyAsset, providerName: String) {
            trackEvent("deposit_view_onramp_insert_amount", hashMapOf("buy_asset" to buyAsset.key, "provider_name" to providerName))
        }

        /**
         * deposit_click_onramp_continue
         *
         * User tapped 'Continue' on the Insert_amount screen, proceeding to the provider flow (Figma: Provider_flow)
         */
        @UiThread
        override fun depositClickOnrampContinue(
            buyAsset: DepositFlowBuyAsset,
            providerName: String,
            buyAmount: Double
        ) {
            val props = hashMapOf(
                "buy_asset" to buyAsset.key,
                "provider_name" to providerName,
                "buy_amount" to buyAmount
            )
            trackEvent("deposit_click_onramp_continue", props)
        }

        /**
         * deposit_view_c2c
         *
         * User sees the crypto-to-crypto purchase screen.
Figma: Buy_ton_with_crypto / Send_token (for TON), or Stablecoin / Send_asset (for USDT)

         */
        @UiThread
        override fun depositViewC2c(buyAsset: DepositFlowBuyAsset, sellAsset: String) {
            trackEvent("deposit_view_c2c", hashMapOf("buy_asset" to buyAsset.key, "sell_asset" to sellAsset))
        }

        /**
         * deposit_view_onramp_flow
         *
         * User is presented with the provider's onramp flow (Figma: Provider_flow). tx_id is the transaction ID generated by Tonkeeper for this onramp session, used to join clickstream data with onramp API data.

         */
        @UiThread
        override fun depositViewOnrampFlow(
            buyAsset: DepositFlowBuyAsset,
            providerName: String,
            buyAmount: Double,
            txId: String
        ) {
            val props = hashMapOf(
                "buy_asset" to buyAsset.key,
                "provider_name" to providerName,
                "buy_amount" to buyAmount,
                "tx_id" to txId
            )
            trackEvent("deposit_view_onramp_flow", props)
        }

        /**
         * deposit_click_receive_tokens
         *
         * User tapped the 'Receive tokens' option on the Deposit screen
         */
        @UiThread
        override fun depositClickReceiveTokens(from: DepositFlowFrom) {
            trackEvent("deposit_click_receive_tokens", hashMapOf("from" to from.key))
        }

        /**
         * deposit_view_receive_tokens
         *
         * User opened the Receive_tokens screen
         */
        @UiThread
        override fun depositViewReceiveTokens(from: DepositFlowFrom) {
            trackEvent("deposit_view_receive_tokens", hashMapOf("from" to from.key))
        }
    }

    class InstallAppImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.InstallApp {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        /**
         * install_app
         *
         * Sent once on the very first app launch after installation. Implemented by storing a firstLaunchTS flag in local storage. iOS only - Android uses legacy 'firstOpen' event instead of install_app.
         */
        @UiThread
        override fun installApp(referrer: String?, deeplink: String?, installerStore: String?) {
            val props = mutableMapOf<String, Any>()
            referrer?.let { props["referrer"] = it }
            deeplink?.let { props["deeplink"] = it }
            installerStore?.let { props["installerStore"] = it }
            trackEvent("install_app", props)
        }
    }

    class LaunchAppImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.LaunchApp {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        /**
         * launch_app
         *
         * Sent every time the user opens the app.
         */
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

        /**
         * onramp_open
         *
         * User on WALLET screen clicked BUY&SELL action button
         */
        @UiThread
        override fun onrampOpen(from: String) {
            trackEvent("onramp_open", hashMapOf("from" to from))
        }

        /**
         * onramp_enter_amount
         *
         * User clicked 'continue' after entering buy/sell amount
         */
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

        /**
         * onramp_continue_to_provider
         *
         * User clicked 'continue' after choosing the payment method and provider
         */
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

        /**
         * onramp_success
         *
         * Onramp transaction completed successfully
         */
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

        /**
         * onramp_fail
         *
         * Onramp transaction failed
         */
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

    class RedOperationsImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.RedOperations {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        /**
         * op_attempt
         *
         * Emitted once per user operation attempt. Use same operation_id in the corresponding op_terminal event.
         */
        @UiThread
        override fun opAttempt(
            operationId: String,
            flow: RedOperationsFlow,
            operation: RedOperationsOperation,
            attemptSource: String?,
            startedAtMs: Int,
            otherMetadata: String?
        ) {
            val props = hashMapOf<String, Any>(
                "operation_id" to operationId,
                "flow" to flow.key,
                "operation" to operation.key,
                "started_at_ms" to startedAtMs
            )
            attemptSource?.let { props["attempt_source"] = it }
            otherMetadata?.let { props["other_metadata"] = it }
            trackEvent("op_attempt", props)
        }

        /**
         * op_terminal
         *
         * Emitted exactly once per operation when it completes (success, fail, or cancel). Must share operation_id with the corresponding op_attempt.
         */
        @UiThread
        override fun opTerminal(
            operationId: String,
            flow: RedOperationsFlow,
            operation: RedOperationsOperation,
            outcome: RedOperationsOutcome,
            durationMs: Double,
            finishedAtMs: Int,
            errorCode: Int?,
            errorMessage: String?,
            errorType: String?,
            stage: String?,
            otherMetadata: String?
        ) {
            val props = hashMapOf<String, Any>(
                "operation_id" to operationId,
                "flow" to flow.key,
                "operation" to operation.key,
                "outcome" to outcome.key,
                "duration_ms" to durationMs,
                "finished_at_ms" to finishedAtMs
            )
            errorCode?.let { props["error_code"] = it }
            errorMessage?.let { props["error_message"] = it }
            errorType?.let { props["error_type"] = it }
            stage?.let { props["stage"] = it }
            otherMetadata?.let { props["other_metadata"] = it }
            trackEvent("op_terminal", props)
        }
    }

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
        @UiThread
        override fun sendOpen(from: SendNativeFrom) {
            trackEvent("send_open", hashMapOf("from" to from.key))
        }

        /**
         * send_click
         *
         * User clicked 'Continue' after entering recipient and amount
         */
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

        /**
         * send_confirm
         *
         * User is on the confirmation screen and slides/clicks "Confirm and Send"
         */
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

        /**
         * send_success
         *
         * Send transaction completed successfully
         */
        @UiThread
        override fun sendSuccess(
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
            trackEvent("send_success", props)
        }

        /**
         * send_failed
         *
         * Send transaction failed
         */
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

        /**
         * staking_open
         *
         * User opened staking page
         */
        @UiThread
        override fun stakingOpen(from: String) {
            trackEvent("staking_open", hashMapOf("from" to from))
        }

        /**
         * staking_plus_input
         *
         * User is presented with the staking input field
         */
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

        /**
         * staking_plus_confirm
         *
         * User is presented with the staking confirmation slider
         */
        @UiThread
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
        @UiThread
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

        /**
         * staking_minus_confirm
         *
         * User is presented with the unstaking confirmation slider
         */
        @UiThread
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
        @UiThread
        override fun stakingMinusSuccess(jettonSymbol: String, providerName: String, providerDomain: String) {
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

        /**
         * swap_open
         *
         * User on WALLET screen clicked SWAP action button
         */
        @UiThread
        override fun swapOpen(type: SwapsNativeType) {
            trackEvent("swap_open", hashMapOf("type" to type.key))
        }

        /**
         * swap_click
         *
         * After filling in swap info, user on SWAP screen clicked CONTINUE action button
         */
        @UiThread
        override fun swapClick(type: SwapsNativeType, jettonSymbolFrom: String, jettonSymbolTo: String) {
            val props = hashMapOf(
                "type" to type.key,
                "jetton_symbol_from" to jettonSymbolFrom,
                "jetton_symbol_to" to jettonSymbolTo
            )
            trackEvent("swap_click", props)
        }

        /**
         * swap_confirm
         *
         * After reviewing the swap info, user on CONFIRM SWAP screen slides CONFIRM action button
         */
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

        /**
         * swap_failed
         *
         * Swap failed
         */
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

        /**
         * swap_success
         *
         * After confirming the swap info, user on CONFIRM ACTION screen have to SLIDE TO CONFIRM
         */
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

        /** tc_request */
        @UiThread
        override fun tcRequest(dappUrl: String) {
            trackEvent("tc_request", hashMapOf("dapp_url" to dappUrl))
        }

        /** tc_connect */
        @UiThread
        override fun tcConnect(dappUrl: String, allowNotifications: Boolean) {
            trackEvent("tc_connect", hashMapOf("dapp_url" to dappUrl, "allow_notifications" to allowNotifications))
        }

        /** tc_view_confirm */
        @UiThread
        override fun tcViewConfirm(dappUrl: String, addressType: TonConnectAddressType) {
            trackEvent("tc_view_confirm", hashMapOf("dapp_url" to dappUrl, "address_type" to addressType.key))
        }

        /** tc_send_success */
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

        /** tc_sign_data_success */
        @UiThread
        override fun tcSignDataSuccess(dappUrl: String, payloadType: TonConnectPayloadType) {
            trackEvent("tc_sign_data_success", hashMapOf("dapp_url" to dappUrl, "payload_type" to payloadType.key))
        }
    }

    class WithdrawFlowImpl(
        private val eventExecutor: EventExecutor,
    ) : Events.WithdrawFlow {

        private fun trackEvent(name: String, params: Map<String, Any>) {
            eventExecutor.trackEvent(name, params)
        }

        /**
         * withdraw_open
         *
         * User entered the Withdraw screen
         */
        @UiThread
        override fun withdrawOpen(from: WithdrawFlowFrom) {
            trackEvent("withdraw_open", hashMapOf("from" to from.key))
        }

        /**
         * withdraw_click_sell
         *
         * User tapped an asset to sell on the Withdraw screen
         */
        @UiThread
        override fun withdrawClickSell(from: WithdrawFlowFrom, sellAsset: WithdrawFlowSellAsset) {
            trackEvent("withdraw_click_sell", hashMapOf("from" to from.key, "sell_asset" to sellAsset.key))
        }

        /**
         * withdraw_click_send_tokens
         *
         * User tapped 'Send tokens' on the Withdraw screen, leading to the send flow
         */
        @UiThread
        override fun withdrawClickSendTokens(from: WithdrawFlowFrom) {
            trackEvent("withdraw_click_send_tokens", hashMapOf("from" to from.key))
        }

        /**
         * withdraw_view_onramp_insert_amount
         *
         * User sees the onramp amount entry screen (Figma: Insert_amount). If the user switches to another provider, this event fires again with the new provider_name.

         */
        @UiThread
        override fun withdrawViewOnrampInsertAmount(sellAsset: WithdrawFlowSellAsset, providerName: String) {
            trackEvent("withdraw_view_onramp_insert_amount", hashMapOf("sell_asset" to sellAsset.key, "provider_name" to providerName))
        }

        /**
         * withdraw_click_onramp_continue
         *
         * User tapped 'Continue' on the Insert_amount screen, proceeding to the provider flow (Figma: Provider_flow)
         */
        @UiThread
        override fun withdrawClickOnrampContinue(
            sellAsset: WithdrawFlowSellAsset,
            providerName: String,
            sellAmount: Double
        ) {
            val props = hashMapOf(
                "sell_asset" to sellAsset.key,
                "provider_name" to providerName,
                "sell_amount" to sellAmount
            )
            trackEvent("withdraw_click_onramp_continue", props)
        }

        /**
         * withdraw_view_onramp_flow
         *
         * User is presented with the provider's flow (Figma: Provider_flow). tx_id is the transaction ID generated by Tonkeeper for this withdrawal attempt, used to join clickstream data with onramp API data.

         */
        @UiThread
        override fun withdrawViewOnrampFlow(
            sellAsset: WithdrawFlowSellAsset,
            providerName: String,
            sellAmount: Double,
            buyAsset: WithdrawFlowBuyAsset,
            txId: String
        ) {
            val props = hashMapOf(
                "sell_asset" to sellAsset.key,
                "provider_name" to providerName,
                "sell_amount" to sellAmount,
                "buy_asset" to buyAsset.key,
                "tx_id" to txId
            )
            trackEvent("withdraw_view_onramp_flow", props)
        }

        /**
         * withdraw_view_p2p
         *
         * User sees the P2P market screen
         */
        @UiThread
        override fun withdrawViewP2p(sellAsset: WithdrawFlowSellAsset, buyAsset: WithdrawFlowBuyAsset) {
            trackEvent("withdraw_view_p2p", hashMapOf("sell_asset" to sellAsset.key, "buy_asset" to buyAsset.key))
        }

        /**
         * withdraw_send_confirm
         *
         * User is on the confirmation screen and slides/clicks 'Confirm' to complete the stablecoin withdrawal (Figma: Confirm_page)
         */
        @UiThread
        override fun withdrawSendConfirm(
            sellAsset: WithdrawFlowSellAsset,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double,
            feePaidIn: WithdrawFlowFeePaidIn
        ) {
            val props = hashMapOf(
                "sell_asset" to sellAsset.key,
                "asset_network" to assetNetwork,
                "token_symbol" to tokenSymbol,
                "amount" to amount,
                "fee_paid_in" to feePaidIn.key
            )
            trackEvent("withdraw_send_confirm", props)
        }

        /**
         * withdraw_send_success
         *
         * Stablecoin withdrawal transaction completed successfully (Figma: Confirm_page/Done)
         */
        @UiThread
        override fun withdrawSendSuccess(
            sellAsset: WithdrawFlowSellAsset,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double,
            feePaidIn: WithdrawFlowFeePaidIn
        ) {
            val props = hashMapOf(
                "sell_asset" to sellAsset.key,
                "asset_network" to assetNetwork,
                "token_symbol" to tokenSymbol,
                "amount" to amount,
                "fee_paid_in" to feePaidIn.key
            )
            trackEvent("withdraw_send_success", props)
        }
    }
}
