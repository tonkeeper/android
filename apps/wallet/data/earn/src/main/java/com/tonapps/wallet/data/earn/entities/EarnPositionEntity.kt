package com.tonapps.wallet.data.earn.entities

import java.math.BigDecimal

data class EarnPositionEntity(
    val network: String,
    val vaultId: String,
    val assetSymbol: String,
    val protocolName: String,
    val balanceUsd: BigDecimal,
    val earnedUsd: BigDecimal,
    val assetIconUrl: String?,
)