package com.tonapps.wallet.data.multichain.account

data class WalletAssetsQuery(
    val walletId: String,
    val currency: String,
    val verifiedOnly: Boolean,
    val hideDust: Boolean,
    val query: String? = null,
    val network: String? = null,
    val availableOnly: Boolean = false,
    val showHidden: Boolean = false,
    val showAll: Boolean = false,
    val capabilities: List<AssetCapability>? = null,
)
