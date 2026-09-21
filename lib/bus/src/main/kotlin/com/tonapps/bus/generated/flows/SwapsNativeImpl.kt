package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeFeeAsset
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeType
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeWalletMode

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
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
    @AnyThread
    override fun swapOpen(type: SwapsNativeType, walletMode: SwapsNativeWalletMode) {
        trackEvent("swap_open", hashMapOf("type" to type.key, "wallet_mode" to walletMode.key))
    }

    /**
     * swap_click
     *
     * After filling in swap info, user on SWAP screen clicked CONTINUE action button
     */
    @AnyThread
    override fun swapClick(
        type: SwapsNativeType,
        walletMode: SwapsNativeWalletMode,
        assetFrom: String,
        assetTo: String,
        isMax: Boolean?
    ) {
        val props = hashMapOf<String, Any>(
            "type" to type.key,
            "wallet_mode" to walletMode.key,
            "asset_from" to assetFrom,
            "asset_to" to assetTo
        )
        isMax?.let { props["is_max"] = it }
        trackEvent("swap_click", props)
    }

    /**
     * swap_confirm
     *
     * After reviewing the swap info, user on CONFIRM SWAP screen slides CONFIRM action button
     */
    @AnyThread
    override fun swapConfirm(
        type: SwapsNativeType,
        walletMode: SwapsNativeWalletMode,
        assetFrom: String,
        assetTo: String,
        feeAsset: SwapsNativeFeeAsset,
        providerName: String,
        isMax: Boolean?
    ) {
        val props = hashMapOf<String, Any>(
            "type" to type.key,
            "wallet_mode" to walletMode.key,
            "asset_from" to assetFrom,
            "asset_to" to assetTo,
            "fee_asset" to feeAsset.key,
            "provider_name" to providerName
        )
        isMax?.let { props["is_max"] = it }
        trackEvent("swap_confirm", props)
    }

    /**
     * swap_failed
     *
     * Swap failed
     */
    @AnyThread
    override fun swapFailed(
        type: SwapsNativeType,
        walletMode: SwapsNativeWalletMode,
        assetFrom: String,
        assetTo: String,
        feeAsset: SwapsNativeFeeAsset,
        providerName: String,
        errorMessage: String,
        isMax: Boolean?
    ) {
        val props = hashMapOf<String, Any>(
            "type" to type.key,
            "wallet_mode" to walletMode.key,
            "asset_from" to assetFrom,
            "asset_to" to assetTo,
            "fee_asset" to feeAsset.key,
            "provider_name" to providerName,
            "error_message" to errorMessage
        )
        isMax?.let { props["is_max"] = it }
        trackEvent("swap_failed", props)
    }

    /**
     * swap_success
     *
     * Swap completed successfully (the swap transaction was submitted)
     */
    @AnyThread
    override fun swapSuccess(
        type: SwapsNativeType,
        walletMode: SwapsNativeWalletMode,
        assetFrom: String,
        assetTo: String,
        feeAsset: SwapsNativeFeeAsset,
        providerName: String,
        isMax: Boolean?
    ) {
        val props = hashMapOf<String, Any>(
            "type" to type.key,
            "wallet_mode" to walletMode.key,
            "asset_from" to assetFrom,
            "asset_to" to assetTo,
            "fee_asset" to feeAsset.key,
            "provider_name" to providerName
        )
        isMax?.let { props["is_max"] = it }
        trackEvent("swap_success", props)
    }
}
