package com.tonapps.core.flags

import com.tonapps.apps.wallet.data.features.BuildConfig

enum class WalletFeatureKey(
    override val featureKey: String,
    // The app follows the key in debug builds only, whatever its stored value says.
    val debugOnly: Boolean = false,
) : FeatureKey {
    IS_SWAPKIT_HARD_SWITCH_ENABLED("android_is_swapkit_hard_switch_enabled"),
    IS_SWAP_EXACT_OUTPUT_ENABLED("android_is_swap_exact_output_enabled"),
    IS_SWAP_V3_ENABLED("android_is_swap_v3_enabled"),
    IS_SWAP_REDESIGN_ENABLED("android_is_swap_redesign_enabled"),
    IS_LIQUID_GLASS_ENABLED("android_is_liquid_glass_enabled"),
    IS_PERPS_ENABLED("android_is_perps_enabled"),
    IS_PERPS_BATTERY_FEE_ENABLED("android_perps_battery_fee_enabled"),
    IS_EARN_ENABLED("android_is_earn_enabled"),
    LOG_DEFAULT_ON("android_log_default_on"),
    IS_IN_APP_REVIEW_ENABLED("android_in_app_review_enabled"),
    TONCONNECT_SIGN_MESSAGE_ENABLED("android_tonconnect_sign_message_enabled"),
    IS_LEDGER_MULTICHAIN_ENABLED("android_is_ledger_multichain_enabled"),
    OPERATION_TRANSFER_ENABLED("android_operation_transfer_enabled"),
    OPERATION_DAPP_ENABLED("android_operation_dapp_enabled"),
    IS_MOONPAY_RAMP_ENABLED("android_moonpay_ramp_enabled"),
    IS_MOONPAY_GOOGLE_PAY_ENABLED("android_moonpay_google_pay_enabled"),
    IS_LOYALTY_ENABLED("android_is_loyalty_enabled"),
    IMPORT_BIP39_AS_LEGACY("android_import_bip39_as_legacy", debugOnly = true),
    IS_LEGACY_BIP39_MIGRATION_ENABLED("android_is_legacy_bip39_migration_enabled"),
    ;

    companion object {

        // The keys this build follows: the Feature Flags screen offers only these, and only these are
        // reported as on.
        val switchable: List<WalletFeatureKey>
            get() = entries.filter { !it.debugOnly || BuildConfig.DEBUG }

        fun asAnalyticsProps(): Map<String, Any> {
            val props = mutableMapOf<String, Any>()
            // Not every entry: a key the build ignores is not a feature that is on.
            switchable.forEach { key ->
                val isEnabled = when (key) {
                    IS_PERPS_ENABLED -> WalletFeature.Perps.isEnabled
                    IS_LIQUID_GLASS_ENABLED -> WalletFeature.LiquidGlass.value
                    else -> FeatureManager.isEnabled(key)
                }
                if (isEnabled) {
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

    /** Multichain swaps quote and prepare on `/v3/crosschain`, which answers with a Generic Payload operation. */
    data object SwapV3 : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_SWAP_V3_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    /** The rollout of one-click swap; it takes effect only where the boot config's `one_click_swap_enabled` allows it. */
    data object SwapRedesign : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_SWAP_REDESIGN_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object LiquidGlass : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_LIQUID_GLASS_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object Perps : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_PERPS_ENABLED

        /**
         * The boot config's `feature_access.perps.state` not being `hidden`: a geo gate on top of the remote rollout
         * flag, closed until the config says otherwise. A dev override skips it.
         */
        @Volatile
        var isAllowedByConfig: () -> Boolean = { false }

        override val isEnabled: Boolean
            get() = super.isEnabled && (isOverridden || FeatureManager.isStaticOverridden(key) || isAllowedByConfig())

        override fun provide(): Boolean {
            return isEnabled
        }

        /** The Perps banner and story share the id `perps` and stay hidden while Perps is off. */
        fun isPromoHidden(id: String): Boolean = id == "perps" && !isEnabled
    }

    data object PerpsBatteryFee : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_PERPS_BATTERY_FEE_ENABLED

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

    /**
     * The old-store wallets made from a BIP39 phrase move to the multichain store on app start; off,
     * they stay where they are and the lock screen does not ask for the passcode to move them.
     */
    data object LegacyBip39Migration : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_LEGACY_BIP39_MIGRATION_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    /**
     * Debug aid: a BIP39 phrase is imported as a legacy wallet, so the legacy-to-multichain migration can
     * be tried. Debug builds only: the key is also a Remote Config one, and a release build must not
     * follow it.
     */
    data object ImportBip39AsLegacy : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IMPORT_BIP39_AS_LEGACY

        override val isEnabled: Boolean get() = BuildConfig.DEBUG && super.isEnabled

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object LedgerMultichain : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_LEDGER_MULTICHAIN_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    /** The wallet's own send confirmed through the operation API and its validator; off, the transaction confirm. */
    data object OperationTransfer : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.OPERATION_TRANSFER_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    /** A dapp's EVM transaction confirmed through the operation API and its validator; off, the transaction confirm. */
    data object OperationDapp : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.OPERATION_DAPP_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object MoonPayRamp : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_MOONPAY_RAMP_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    // Google Pay in MoonPay's frames works only once Google approves the app under MoonPay's profile.
    data object MoonPayGooglePay : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_MOONPAY_GOOGLE_PAY_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }

    data object Loyalty : Features<Boolean>(), WalletFeature {
        override val key: FeatureKey get() = WalletFeatureKey.IS_LOYALTY_ENABLED

        override fun provide(): Boolean {
            return isEnabled
        }
    }
}
