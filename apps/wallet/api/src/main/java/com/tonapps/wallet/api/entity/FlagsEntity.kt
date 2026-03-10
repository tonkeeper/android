package com.tonapps.wallet.api.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class FlagsEntity(
    val disableSwap: Boolean,
    val disableExchangeMethods: Boolean,
    val disableDApps: Boolean,
    val disableSigner: Boolean,
    val safeModeEnabled: Boolean,
    val disableStaking: Boolean,
    val disableTron: Boolean,
    val disableBattery: Boolean,
    val disableGasless: Boolean,
    val disableUsde: Boolean,
    val disableNativeSwap: Boolean,
    val disableOnboardingStory: Boolean,
    val disableNfts: Boolean,
    val disableWalletKit: Boolean
) : Parcelable {

    constructor(json: JSONObject) : this(
        disableSwap = json.optBoolean("disable_swap", false),
        disableExchangeMethods = json.optBoolean("disable_exchange_methods", false),
        disableDApps = json.optBoolean("disable_dapps", false),
        disableSigner = json.optBoolean("disable_signer", false),
        safeModeEnabled = json.optBoolean("safe_mode_enabled", false),
        disableStaking = json.optBoolean("disable_staking", false),
        disableTron = json.optBoolean("disable_tron", false),
        disableBattery = json.optBoolean("disable_battery", false),
        disableGasless = json.optBoolean("disable_gaseless", false),
        disableUsde = json.optBoolean("disable_usde", false),
        disableNativeSwap = json.optBoolean("disable_native_swap", false),
        disableOnboardingStory = json.optBoolean("disable_onboarding_story", false),
        disableNfts = json.optBoolean("disable_nfts", false),
        disableWalletKit = json.optBoolean("disable_wallet_kit", true)
    )

    constructor() : this(
        disableSwap = false,
        disableExchangeMethods = false,
        disableDApps = false,
        disableSigner = false,
        safeModeEnabled = false,
        disableStaking = false,
        disableTron = false,
        disableBattery = false,
        disableGasless = false,
        disableUsde = false,
        disableNativeSwap = false,
        disableOnboardingStory = false,
        disableNfts = false,
        disableWalletKit = true,
    )
}