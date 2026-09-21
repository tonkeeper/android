package com.tonapps.core.flags

enum class WalletFeatureKey(
    override val featureKey: String,
) : FeatureKey {
    IS_MULTICHAIN_ENABLED("android_is_multichain_enabled"),
    IS_IMPORT_MULTICHAIN_WALLET("android_is_import_multichain_wallet"),
    IS_SWAPKIT_HARD_SWITCH_ENABLED("android_is_swapkit_hard_switch_enabled"),
    IS_SWAP_EXACT_OUTPUT_ENABLED("android_is_swap_exact_output_enabled"),
    IS_WALLETKIT_ENABLED("walletkitEnabled"),
    IS_MIGRATION_ENABLED("android_is_migration_enabled"),
    IS_MIGRATION_BATTERY_ENABLED("android_is_migration_battery_enabled"),
    IS_RAFFLES_ENABLED("android_is_raffles_enabled"),
    IS_PERPS_ENABLED("android_is_perps_enabled"),
    IS_REALTIME_ENABLED("android_is_realtime_enabled"),
    LOG_DEFAULT_ON("android_log_default_on"),
    IS_IN_APP_REVIEW_ENABLED("android_in_app_review_enabled"),
    ;

    companion object {

        fun asAnalyticsProps(overrides: Map<WalletFeatureKey, Boolean> = emptyMap()): Map<String, Any> {
            val props = mutableMapOf<String, Any>()
            entries.forEach { key ->
                if (overrides[key] ?: FeatureManager.isEnabled(key)) {
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

    data object Multichain : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_MULTICHAIN_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object ImportMultichainWallet : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_IMPORT_MULTICHAIN_WALLET

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

    data object Migration : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_MIGRATION_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object MigrationBattery : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_MIGRATION_BATTERY_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object Raffles : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_RAFFLES_ENABLED

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

    data object Realtime : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_REALTIME_ENABLED

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
}
