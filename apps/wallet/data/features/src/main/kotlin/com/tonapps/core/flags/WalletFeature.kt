package com.tonapps.core.flags

enum class WalletFeatureKey(
    override val featureKey: String,
) : FeatureKey {
    IS_NEW_RAMP_FLOW_ENABLED("android_is_new_ramp_flow_enabled"),
    IS_TRADING_TAB_ENABLED("android_is_trading_tab_enabled"),
    IS_STREAMING_V2_ENABLED("android_is_streaming_api_v2_enabled"),
    ;
}

sealed interface WalletFeature {
    data object NewRampFlow : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_NEW_RAMP_FLOW_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object TradingTab : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_TRADING_TAB_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object StreamingV2 : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_STREAMING_V2_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }
}
