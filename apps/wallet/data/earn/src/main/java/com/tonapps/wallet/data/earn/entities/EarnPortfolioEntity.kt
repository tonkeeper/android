package com.tonapps.wallet.data.earn.entities

import java.math.BigDecimal

data class EarnPortfolioEntity(
    val currency: String,
    val totalFiat: BigDecimal?,
    val totalUsd: BigDecimal,
    val positions: List<EarnPositionEntity>,
)
