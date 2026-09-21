package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowAddFundsOption
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowBuyAsset
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowNetwork

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class DepositFlowImpl(
    private val eventExecutor: EventExecutor,
) : Events.DepositFlow {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * deposit_started
     *
     * User entered the Add Funds screen
     */
    @AnyThread
    override fun depositStarted(from: DepositFlowFrom, availableOptions: String) {
        trackEvent("deposit_started", hashMapOf("from" to from.key, "available_options" to availableOptions))
    }

    /**
     * deposit_option_click
     *
     * User tapped an option on the Add Funds screen
     */
    @AnyThread
    override fun depositOptionClick(from: DepositFlowFrom, addFundsOption: DepositFlowAddFundsOption) {
        trackEvent("deposit_option_click", hashMapOf("from" to from.key, "add_funds_option" to addFundsOption.key))
    }

    /**
     * deposit_view_receive_tokens
     *
     * User sees the Receive tokens screen with QR code and address
     */
    @AnyThread
    override fun depositViewReceiveTokens(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        network: DepositFlowNetwork
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "network" to network.key
        )
        trackEvent("deposit_view_receive_tokens", props)
    }

    /**
     * deposit_view_p2p_alert
     *
     * User sees the "Buy via Wallet P2P" modal screen
     */
    @AnyThread
    override fun depositViewP2pAlert(from: DepositFlowFrom, addFundsOption: DepositFlowAddFundsOption) {
        trackEvent("deposit_view_p2p_alert", hashMapOf("from" to from.key, "add_funds_option" to addFundsOption.key))
    }

    /**
     * deposit_continue_to_p2p_market
     *
     * User was sent to the external Wallet P2P flow
     */
    @AnyThread
    override fun depositContinueToP2pMarket(from: DepositFlowFrom, addFundsOption: DepositFlowAddFundsOption) {
        trackEvent("deposit_continue_to_p2p_market", hashMapOf("from" to from.key, "add_funds_option" to addFundsOption.key))
    }

    /**
     * deposit_view_buy_ton_with_crypto
     *
     * User sees the "Payment method" screen listing cryptos that can be swapped into TON (BTC, USDT, ETH, SOL, etc.)

     */
    @AnyThread
    override fun depositViewBuyTonWithCrypto(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: DepositFlowBuyAsset,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset.key,
            "available_options" to availableOptions
        )
        trackEvent("deposit_view_buy_ton_with_crypto", props)
    }

    /**
     * deposit_view_send_asset
     *
     * User sees the deposit instructions screen with address, min/max amounts, network, and estimated arrival time. Shared across buy_ton_with_crypto and buy_with_stablecoins flows.

     */
    @AnyThread
    override fun depositViewSendAsset(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        sellAsset: String,
        buyAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset
        )
        trackEvent("deposit_view_send_asset", props)
    }

    /**
     * deposit_view_qr_code
     *
     * User opened the Payment QR code screen to scan or copy the deposit address. Shared across buy_ton_with_crypto and buy_with_stablecoins flows.

     */
    @AnyThread
    override fun depositViewQrCode(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        sellAsset: String,
        buyAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset
        )
        trackEvent("deposit_view_qr_code", props)
    }

    /**
     * deposit_view_fiat_choose_asset
     *
     * User sees the "Choose asset" screen listing assets available for purchase with fiat (TON, USDT TON, USDT TRC20, etc.)

     */
    @AnyThread
    override fun depositViewFiatChooseAsset(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "available_options" to availableOptions
        )
        trackEvent("deposit_view_fiat_choose_asset", props)
    }

    /**
     * deposit_click_fiat_asset
     *
     * User selected an asset on the "Choose asset" screen
     */
    @AnyThread
    override fun depositClickFiatAsset(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset
        )
        trackEvent("deposit_click_fiat_asset", props)
    }

    /**
     * deposit_view_fiat_payment_method
     *
     * User sees the "Payment method" screen listing fiat payment options (Apple Pay, Debit Card, PayPal, Revolut Pay, Venmo, Volt, P2P Market)

     */
    @AnyThread
    override fun depositViewFiatPaymentMethod(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        sellAsset: String,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "sell_asset" to sellAsset,
            "available_options" to availableOptions
        )
        trackEvent("deposit_view_fiat_payment_method", props)
    }

    /**
     * deposit_click_fiat_payment_method
     *
     * User selected a payment method on the "Payment method" screen
     */
    @AnyThread
    override fun depositClickFiatPaymentMethod(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        sellAsset: String,
        paymentMethod: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "sell_asset" to sellAsset,
            "payment_method" to paymentMethod
        )
        trackEvent("deposit_click_fiat_payment_method", props)
    }

    /**
     * deposit_view_ramp_insert_amount
     *
     * User sees the amount entry screen for the onramp purchase
     */
    @AnyThread
    override fun depositViewRampInsertAmount(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        sellAsset: String,
        paymentMethod: String,
        providerName: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "sell_asset" to sellAsset,
            "payment_method" to paymentMethod,
            "provider_name" to providerName
        )
        trackEvent("deposit_view_ramp_insert_amount", props)
    }

    /**
     * deposit_click_ramp_insert_amount_continue
     *
     * User tapped "Continue" on the ramp insert amount screen
     */
    @AnyThread
    override fun depositClickRampInsertAmountContinue(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        sellAsset: String,
        paymentMethod: String,
        providerName: String,
        amount: Double
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "sell_asset" to sellAsset,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "amount" to amount
        )
        trackEvent("deposit_click_ramp_insert_amount_continue", props)
    }

    /**
     * deposit_view_ramp_alert
     *
     * User sees the external app alert modal (e.g. "Mercuryo — You are opening an external app not operated by Tonkeeper") before being sent to the onramp provider

     */
    @AnyThread
    override fun depositViewRampAlert(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        sellAsset: String,
        paymentMethod: String,
        providerName: String,
        amount: Double
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "sell_asset" to sellAsset,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "amount" to amount
        )
        trackEvent("deposit_view_ramp_alert", props)
    }

    /**
     * deposit_continue_to_ramp_provider
     *
     * User is presented with the provider's onramp flow. tx_id is the transaction ID generated by Tonkeeper for this onramp session, used to join clickstream data with onramp API data.

     */
    @AnyThread
    override fun depositContinueToRampProvider(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        sellAsset: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
        txId: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "sell_asset" to sellAsset,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "amount" to amount,
            "tx_id" to txId
        )
        trackEvent("deposit_continue_to_ramp_provider", props)
    }

    /**
     * deposit_view_choose_stablecoin
     *
     * User sees the "Choose asset" screen listing stablecoins available to receive (USDT TON, USDT TRC20)

     */
    @AnyThread
    override fun depositViewChooseStablecoin(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "available_options" to availableOptions
        )
        trackEvent("deposit_view_choose_stablecoin", props)
    }

    /**
     * deposit_click_stablecoin
     *
     * User selected a stablecoin to receive on the "Choose asset" screen
     */
    @AnyThread
    override fun depositClickStablecoin(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset
        )
        trackEvent("deposit_click_stablecoin", props)
    }

    /**
     * deposit_view_stablecoin_payment_method
     *
     * User sees the "Payment method" screen listing stablecoins to pay with (USDC, USDT, DAI, etc.)

     */
    @AnyThread
    override fun depositViewStablecoinPaymentMethod(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "available_options" to availableOptions
        )
        trackEvent("deposit_view_stablecoin_payment_method", props)
    }

    /**
     * deposit_click_stablecoin_payment_method
     *
     * User selected a stablecoin to pay with on the "Payment method" screen
     */
    @AnyThread
    override fun depositClickStablecoinPaymentMethod(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        stablecoinSymbol: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "stablecoin_symbol" to stablecoinSymbol
        )
        trackEvent("deposit_click_stablecoin_payment_method", props)
    }

    /**
     * deposit_view_choose_network
     *
     * User sees the "Choose network" screen listing available networks for the selected stablecoin (ERC20, SPL, POL, ARB, Base, etc.)

     */
    @AnyThread
    override fun depositViewChooseNetwork(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        stablecoinSymbol: String,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "stablecoin_symbol" to stablecoinSymbol,
            "available_options" to availableOptions
        )
        trackEvent("deposit_view_choose_network", props)
    }

    /**
     * deposit_click_network
     *
     * User selected a network on the "Choose network" screen
     */
    @AnyThread
    override fun depositClickNetwork(
        from: DepositFlowFrom,
        addFundsOption: DepositFlowAddFundsOption,
        buyAsset: String,
        stablecoinSymbol: String,
        sellAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "add_funds_option" to addFundsOption.key,
            "buy_asset" to buyAsset,
            "stablecoin_symbol" to stablecoinSymbol,
            "sell_asset" to sellAsset
        )
        trackEvent("deposit_click_network", props)
    }
}
