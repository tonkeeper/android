package com.tonapps.portfolio.screens.wallet

import androidx.annotation.DrawableRes

data class EarnPositionUi(
    val network: String,
    val vaultId: String,
    val assetSymbol: String,
    val assetIconUrl: String?,
    @get:DrawableRes val networkIcon: Int?,
    val networkName: String,
    val protocolName: String,
    val balanceFormat: String,
    val earnedFormat: String,
) {
    val id: String
        get() = "$network/$vaultId"
}