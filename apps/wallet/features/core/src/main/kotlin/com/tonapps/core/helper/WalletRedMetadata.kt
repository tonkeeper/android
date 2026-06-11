package com.tonapps.core.helper

import com.tonapps.bus.core.contract.RedMetadata
import com.tonapps.core.flags.WalletFeature

object WalletRedMetadata {
    fun walletKit(isEnabled: Boolean = WalletFeature.WalletKitEnabled.isEnabled): String {
        return RedMetadata.builder {
            add("is_wallet_kit_enabled", isEnabled)
        }
    }
}