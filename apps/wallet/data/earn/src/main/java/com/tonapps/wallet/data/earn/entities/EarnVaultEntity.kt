package com.tonapps.wallet.data.earn.entities

import java.math.BigDecimal

data class EarnVaultEntity(
    val network: String,
    val vaultId: String,
    val assetSymbol: String,
    val protocolName: String,
    val apy: BigDecimal,
    val assetIconUrl: String?,
)