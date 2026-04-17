package com.tonapps.core.flags

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.tonapps.log.L

@Deprecated("Use this class through Features")
class RemoteConfig : RemoteConfigProvider {

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance()
    }

    private enum class FeatureFlag(val key: String) {
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
            FeatureFlag.ONBOARDING_STORIES_ENABLED.key to true,
            WalletFeatureKey.IS_NEW_RAMP_FLOW_ENABLED.featureKey to true
        )

        remoteConfig.setDefaultsAsync(defaults)
    }

    val inAppUpdateAvailable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IN_APP_UPDATE_AVAILABLE.key)

    val nativeOnrmapEnabled: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.NATIVE_ONRAMP_ENABLED.key)

    val isCountryPickerDisable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IS_COUNTRY_PICKER_DISABLE.key)

    val isOnboardingStoriesEnabled: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.ONBOARDING_STORIES_ENABLED.key)

    override fun isFeatureEnabled(feature: FeatureKey): Boolean {
        return remoteConfig.getBoolean(feature.featureKey)
    }

    override fun getFeatureValue(feature: FeatureKey, default: String): String {
        val value = remoteConfig.getString(feature.featureKey)
        return value.ifEmpty { default }
    }

    override fun optFeatureValue(feature: FeatureKey): String? {
        val value = remoteConfig.getString(feature.featureKey)
        return value.ifEmpty { null }
    }

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
