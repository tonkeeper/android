package com.tonapps.core.flags

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.tonapps.apps.wallet.data.features.BuildConfig
import com.tonapps.log.L

@Deprecated("Use this class through Features")
class RemoteConfig : RemoteConfigProvider {

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance()
    }

    private enum class FeatureFlag(val key: String) {
        IN_APP_UPDATE_AVAILABLE("inAppUpdateAvailable"),
        ONBOARDING_STORIES_ENABLED("onboarding_stories_enabled");
    }

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(600)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaults = mapOf(
            FeatureFlag.ONBOARDING_STORIES_ENABLED.key to true,
            WalletFeatureKey.IS_SWAPKIT_HARD_SWITCH_ENABLED.featureKey to false,
            WalletFeatureKey.IS_SWAP_EXACT_OUTPUT_ENABLED.featureKey to false,
            WalletFeatureKey.IS_SWAP_V3_ENABLED.featureKey to true,
            WalletFeatureKey.IS_SWAP_REDESIGN_ENABLED.featureKey to false,
            WalletFeatureKey.IS_LIQUID_GLASS_ENABLED.featureKey to BuildConfig.DEBUG,
            WalletFeatureKey.IS_PERPS_ENABLED.featureKey to false,
            WalletFeatureKey.IS_PERPS_BATTERY_FEE_ENABLED.featureKey to false,
            WalletFeatureKey.IS_EARN_ENABLED.featureKey to false,
            WalletFeatureKey.LOG_DEFAULT_ON.featureKey to false,
            WalletFeatureKey.IS_IN_APP_REVIEW_ENABLED.featureKey to false,
            WalletFeatureKey.TONCONNECT_SIGN_MESSAGE_ENABLED.featureKey to true,
            WalletFeatureKey.IS_LEDGER_MULTICHAIN_ENABLED.featureKey to BuildConfig.DEBUG,
            WalletFeatureKey.OPERATION_TRANSFER_ENABLED.featureKey to true,
            WalletFeatureKey.OPERATION_DAPP_ENABLED.featureKey to true,
            WalletFeatureKey.IS_MOONPAY_RAMP_ENABLED.featureKey to false,
            WalletFeatureKey.IS_MOONPAY_GOOGLE_PAY_ENABLED.featureKey to false,
            WalletFeatureKey.IS_LOYALTY_ENABLED.featureKey to false,
            WalletFeatureKey.IMPORT_BIP39_AS_LEGACY.featureKey to false,
            WalletFeatureKey.IS_LEGACY_BIP39_MIGRATION_ENABLED.featureKey to false,
        )

        remoteConfig.setDefaultsAsync(defaults)
    }

    val inAppUpdateAvailable: Boolean
        get() = remoteConfig.getBoolean(FeatureFlag.IN_APP_UPDATE_AVAILABLE.key)

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

    override fun isRemoteResolved(feature: FeatureKey): Boolean {
        return remoteConfig.getValue(feature.featureKey).source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
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
