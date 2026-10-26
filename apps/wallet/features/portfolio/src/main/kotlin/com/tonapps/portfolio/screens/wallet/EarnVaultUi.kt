package com.tonapps.portfolio.screens.wallet

import androidx.annotation.DrawableRes

data class EarnVaultUi(
    val network: String,
    val vaultId: String,
    val assetSymbol: String,
    val assetIconUrl: String?,
    @get:DrawableRes val networkIcon: Int?,
    val apyFormat: String,
    val protocolName: String,
) {
    val id: String
        get() = "$network/$vaultId"
}