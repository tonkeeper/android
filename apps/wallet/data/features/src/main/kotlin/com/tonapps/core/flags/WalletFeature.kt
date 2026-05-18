package com.tonapps.core.flags

enum class WalletFeatureKey(
    override val featureKey: String,
) : FeatureKey {
    IS_TRADING_TAB_ENABLED("android_is_trading_tab_enabled"),
    IS_STREAMING_V2_ENABLED("android_is_streaming_api_v2_enabled"),
    IS_WALLETKIT_ENABLED("walletkitEnabled"),
    ;
}

sealed interface WalletFeature {
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

    data object WalletKitEnabled : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_WALLETKIT_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }
}
