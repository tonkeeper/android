package com.tonapps.tonkeeper

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class RemoteConfig(context: Context) {
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    private enum class FeatureFlag(val key: String) {
        IS_SWAP_DISABLE("isSwapDisable"),
        IS_STAKING_DISABLE("isStakingDisable"),
        IS_DAPPS_DISABLE("isDappsDisable"),
        DISABLE_BATTERY_CRYPTO_RECHARGE_MODULE("disableBatteryCryptoRechargeModule"),
        HARDCODED_COUNTRY_CODE("hardcodedCountryCode"),
        IN_APP_UPDATE_AVAILABLE("inAppUpdateAvailable"),
        IS_COUNTRY_PICKER_DISABLE("isCountryPickerDisable"),
        IS_TRON_DISABLED("isTronDisabled"),
        NATIVE_ONRAMP_ENABLED("native_onrmap_enabled"),
        ETHENA_ENABLED("ethena_enabled");
    }

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaults = mapOf(
            FeatureFlag.IS_SWAP_DISABLE.key to true,
            FeatureFlag.IS_STAKING_DISABLE.key to true,
            FeatureFlag.IS_DAPPS_DISABLE.key to true,
            FeatureFlag.DISABLE_BATTERY_CRYPTO_RECHARGE_MODULE.key to true,
            FeatureFlag.IS_TRON_DISABLED.key to false
        )

        remoteConfig.setDefaultsAsync(defaults)
    }

    fun fetchAndActivate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("RemoteConfig", "Fetched and activated successfully")
            } else {
                Log.e("RemoteConfig", "Fetch failed, using defaults")
            }
        }
    }

    val inAppUpdateAvailable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IN_APP_UPDATE_AVAILABLE.key)

    val isSwapDisable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IS_SWAP_DISABLE.key)

    val nativeOnrmapEnabled: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.NATIVE_ONRAMP_ENABLED.key)

    val isCountryPickerDisable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IS_COUNTRY_PICKER_DISABLE.key)

    val isStakingDisable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IS_STAKING_DISABLE.key)

    val hardcodedCountryCode: String?
        get() = remoteConfig.getString(FeatureFlag.HARDCODED_COUNTRY_CODE.key).takeIf { it.isNotEmpty() }

    val isDappsDisable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IS_DAPPS_DISABLE.key)

    val isBatteryCryptoRechargeDisable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.DISABLE_BATTERY_CRYPTO_RECHARGE_MODULE.key)

    val isTronDisabled: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IS_TRON_DISABLED.key)

    val isEthenaEnabled: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.ETHENA_ENABLED.key)
}