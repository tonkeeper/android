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
        IS_COUNTRY_PICKER_DISABLE("isCountryPickerDisable");
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
            FeatureFlag.DISABLE_BATTERY_CRYPTO_RECHARGE_MODULE.key to true
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

    val isSwapDisable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IS_SWAP_DISABLE.key)

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
}