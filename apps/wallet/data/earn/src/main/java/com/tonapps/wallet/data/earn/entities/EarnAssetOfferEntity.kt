package com.tonapps.wallet.data.earn.entities

import java.math.BigDecimal

data class EarnAssetOfferEntity(
    val assetId: String,
    val network: String,
    val vaultId: String,
    val apy: BigDecimal,
)
