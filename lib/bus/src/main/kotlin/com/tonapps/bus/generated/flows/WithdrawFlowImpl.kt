package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFeeAsset
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowWithdrawOption

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class WithdrawFlowImpl(
    private val eventExecutor: EventExecutor,
) : Events.WithdrawFlow {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * withdraw_started
     *
     * User entered the Withdraw screen
     */
    @AnyThread
    override fun withdrawStarted(from: WithdrawFlowFrom, availableOptions: String) {
        trackEvent("withdraw_started", hashMapOf("from" to from.key, "available_options" to availableOptions))
    }

    /**
     * withdraw_option_click
     *
     * User tapped an option on the Withdraw screen
     */
    @AnyThread
    override fun withdrawOptionClick(from: WithdrawFlowFrom, withdrawOption: WithdrawFlowWithdrawOption) {
        trackEvent("withdraw_option_click", hashMapOf("from" to from.key, "withdraw_option" to withdrawOption.key))
    }

    /**
     * withdraw_view_fiat_choose_asset
     *
     * User sees the "Choose asset" screen listing assets available for sale to fiat (TON, USDT, etc.)

     */
    @AnyThread
    override fun withdrawViewFiatChooseAsset(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "available_options" to availableOptions
        )
        trackEvent("withdraw_view_fiat_choose_asset", props)
    }

    /**
     * withdraw_click_fiat_asset
     *
     * User selected an asset on the "Choose asset" screen
     */
    @AnyThread
    override fun withdrawClickFiatAsset(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset
        )
        trackEvent("withdraw_click_fiat_asset", props)
    }

    /**
     * withdraw_view_fiat_payment_method
     *
     * User sees the "Payment method" screen listing fiat payout options (SEPA, PayPal, Debit Card, etc.)

     */
    @AnyThread
    override fun withdrawViewFiatPaymentMethod(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        buyAsset: String,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset,
            "available_options" to availableOptions
        )
        trackEvent("withdraw_view_fiat_payment_method", props)
    }

    /**
     * withdraw_click_fiat_payment_method
     *
     * User selected a payment method on the "Payment method" screen
     */
    @AnyThread
    override fun withdrawClickFiatPaymentMethod(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        buyAsset: String,
        paymentMethod: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset,
            "payment_method" to paymentMethod
        )
        trackEvent("withdraw_click_fiat_payment_method", props)
    }

    /**
     * withdraw_view_ramp_insert_amount
     *
     * User sees the amount entry screen for the offramp sale
     */
    @AnyThread
    override fun withdrawViewRampInsertAmount(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        buyAsset: String,
        paymentMethod: String,
        providerName: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset,
            "payment_method" to paymentMethod,
            "provider_name" to providerName
        )
        trackEvent("withdraw_view_ramp_insert_amount", props)
    }

    /**
     * withdraw_click_ramp_insert_amount_continue
     *
     * User tapped "Continue" on the ramp insert amount screen
     */
    @AnyThread
    override fun withdrawClickRampInsertAmountContinue(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        buyAsset: String,
        paymentMethod: String,
        providerName: String,
        amount: Double
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "amount" to amount
        )
        trackEvent("withdraw_click_ramp_insert_amount_continue", props)
    }

    /**
     * withdraw_view_ramp_alert
     *
     * User sees the external app alert modal before being sent to the offramp provider

     */
    @AnyThread
    override fun withdrawViewRampAlert(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        buyAsset: String,
        paymentMethod: String,
        providerName: String,
        amount: Double
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "amount" to amount
        )
        trackEvent("withdraw_view_ramp_alert", props)
    }

    /**
     * withdraw_continue_to_ramp_provider
     *
     * User is presented with the provider's offramp flow. tx_id is the transaction ID generated by Tonkeeper for this offramp session, used to join clickstream data with offramp API data.

     */
    @AnyThread
    override fun withdrawContinueToRampProvider(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        buyAsset: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
        txId: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "buy_asset" to buyAsset,
            "payment_method" to paymentMethod,
            "provider_name" to providerName,
            "amount" to amount,
            "tx_id" to txId
        )
        trackEvent("withdraw_continue_to_ramp_provider", props)
    }

    /**
     * withdraw_view_choose_asset
     *
     * User sees the "Asset to withdraw" screen listing assets available for cross-chain withdrawal (USDT TON, USDT TRON, etc.)

     */
    @AnyThread
    override fun withdrawViewChooseAsset(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "available_options" to availableOptions
        )
        trackEvent("withdraw_view_choose_asset", props)
    }

    /**
     * withdraw_click_asset
     *
     * User selected an asset on the "Asset to withdraw" screen
     */
    @AnyThread
    override fun withdrawClickAsset(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset
        )
        trackEvent("withdraw_click_asset", props)
    }

    /**
     * withdraw_view_choose_stablecoin
     *
     * User sees the "Asset to receive" screen listing stablecoins available to receive (USDC, USDT, DAI, etc.)

     */
    @AnyThread
    override fun withdrawViewChooseStablecoin(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "available_options" to availableOptions
        )
        trackEvent("withdraw_view_choose_stablecoin", props)
    }

    /**
     * withdraw_click_stablecoin
     *
     * User selected a stablecoin to receive on the "Asset to receive" screen
     */
    @AnyThread
    override fun withdrawClickStablecoin(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        stablecoinSymbol: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "stablecoin_symbol" to stablecoinSymbol
        )
        trackEvent("withdraw_click_stablecoin", props)
    }

    /**
     * withdraw_view_choose_network
     *
     * User sees the "Choose network" screen listing available networks for the selected stablecoin (Ethereum ERC20, Solana SPL, Polygon POL, Arbitrum ARB, Base, etc.)

     */
    @AnyThread
    override fun withdrawViewChooseNetwork(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        stablecoinSymbol: String,
        availableOptions: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "stablecoin_symbol" to stablecoinSymbol,
            "available_options" to availableOptions
        )
        trackEvent("withdraw_view_choose_network", props)
    }

    /**
     * withdraw_click_network
     *
     * User selected a network on the "Choose network" screen
     */
    @AnyThread
    override fun withdrawClickNetwork(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        stablecoinSymbol: String,
        buyAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "stablecoin_symbol" to stablecoinSymbol,
            "buy_asset" to buyAsset
        )
        trackEvent("withdraw_click_network", props)
    }

    /**
     * withdraw_view_insert_amount
     *
     * User sees the "Receive [stablecoin] [network]" screen where they enter the destination address and amount to withdraw

     */
    @AnyThread
    override fun withdrawViewInsertAmount(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        stablecoinSymbol: String,
        buyAsset: String
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "stablecoin_symbol" to stablecoinSymbol,
            "buy_asset" to buyAsset
        )
        trackEvent("withdraw_view_insert_amount", props)
    }

    /**
     * withdraw_click_insert_amount_continue
     *
     * User tapped "Continue" on the amount/address screen
     */
    @AnyThread
    override fun withdrawClickInsertAmountContinue(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        stablecoinSymbol: String,
        buyAsset: String,
        amount: Double
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "stablecoin_symbol" to stablecoinSymbol,
            "buy_asset" to buyAsset,
            "amount" to amount
        )
        trackEvent("withdraw_click_insert_amount_continue", props)
    }

    /**
     * withdraw_send_confirm
     *
     * User is on the confirmation screen showing fees, total amount, and withdrawal time, and slides/clicks "Confirm" to complete the stablecoin withdrawal (powered by Changelly)

     */
    @AnyThread
    override fun withdrawSendConfirm(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        stablecoinSymbol: String,
        buyAsset: String,
        amount: Double,
        feeAsset: WithdrawFlowFeeAsset
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "stablecoin_symbol" to stablecoinSymbol,
            "buy_asset" to buyAsset,
            "amount" to amount,
            "fee_asset" to feeAsset.key
        )
        trackEvent("withdraw_send_confirm", props)
    }

    /**
     * withdraw_send_success
     *
     * Stablecoin withdrawal transaction completed successfully
     */
    @AnyThread
    override fun withdrawSendSuccess(
        from: WithdrawFlowFrom,
        withdrawOption: WithdrawFlowWithdrawOption,
        sellAsset: String,
        stablecoinSymbol: String,
        buyAsset: String,
        amount: Double,
        feeAsset: WithdrawFlowFeeAsset
    ) {
        val props = hashMapOf(
            "from" to from.key,
            "withdraw_option" to withdrawOption.key,
            "sell_asset" to sellAsset,
            "stablecoin_symbol" to stablecoinSymbol,
            "buy_asset" to buyAsset,
            "amount" to amount,
            "fee_asset" to feeAsset.key
        )
        trackEvent("withdraw_send_success", props)
    }
}
