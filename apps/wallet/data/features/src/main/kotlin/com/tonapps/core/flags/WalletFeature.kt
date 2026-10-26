package com.tonapps.core.flags

enum class WalletFeatureKey(
    override val featureKey: String,
) : FeatureKey {
    IS_SWAPKIT_HARD_SWITCH_ENABLED("android_is_swapkit_hard_switch_enabled"),
    IS_SWAP_EXACT_OUTPUT_ENABLED("android_is_swap_exact_output_enabled"),
    IS_PERPS_ENABLED("android_is_perps_enabled"),
    IS_EARN_ENABLED("android_is_earn_enabled"),
    LOG_DEFAULT_ON("android_log_default_on"),
    IS_IN_APP_REVIEW_ENABLED("android_in_app_review_enabled"),
    ANALYTICS_PERSISTENT_CACHE("android_analytics_persistent_cache"),
    TONCONNECT_SIGN_MESSAGE_ENABLED("android_tonconnect_sign_message_enabled"),
    ;

    companion object {

        fun asAnalyticsProps(): Map<String, Any> {
            val props = mutableMapOf<String, Any>()
            entries.forEach { key ->
                if (FeatureManager.isEnabled(key)) {
                    props["ff_${key.featureKey}"] = true
                }
            }
            return props
        }
    }
}

sealed interface WalletFeature {
    data object SwapKit : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_SWAPKIT_HARD_SWITCH_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object SwapExactOutput : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_SWAP_EXACT_OUTPUT_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object Perps : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_PERPS_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object Earn : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_EARN_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object InAppReview : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_IN_APP_REVIEW_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    /** TON Connect `signMessage`: signing internal messages for a relayer without broadcasting them. */
    data object TonConnectSignMessage : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.TONCONNECT_SIGN_MESSAGE_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }
}