package com.tonapps.tonkeeper

import android.content.Context
import com.tonapps.log.L
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.tonapps.wallet.api.API

class RemoteConfig(context: Context, private val api: API) {
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    private enum class FeatureFlag(val key: String) {
        HARDCODED_COUNTRY_CODE("hardcodedCountryCode"),
        IN_APP_UPDATE_AVAILABLE("inAppUpdateAvailable"),
        IS_COUNTRY_PICKER_DISABLE("isCountryPickerDisable"),
        NATIVE_ONRAMP_ENABLED("native_onrmap_enabled"),
        ONBOARDING_STORIES_ENABLED("onboarding_stories_enabled");
    }

    //
    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaults = mapOf(
            FeatureFlag.ONBOARDING_STORIES_ENABLED.key to true
        )

        remoteConfig.setDefaultsAsync(defaults)
    }

    val inAppUpdateAvailable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IN_APP_UPDATE_AVAILABLE.key)

    val nativeOnrmapEnabled: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.NATIVE_ONRAMP_ENABLED.key)

    val isCountryPickerDisable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IS_COUNTRY_PICKER_DISABLE.key)

    val hardcodedCountryCode: String?
        get() = remoteConfig.getString(FeatureFlag.HARDCODED_COUNTRY_CODE.key).takeIf { it.isNotEmpty() }

    val isOnboardingStoriesEnabled: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.ONBOARDING_STORIES_ENABLED.key)

    fun fetchAndActivate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                L.d("RemoteConfig", "Fetched and activated successfully")
            } else {
                L.e("RemoteConfig", "Fetch failed, using defaults")
            }
        }
    }
}
