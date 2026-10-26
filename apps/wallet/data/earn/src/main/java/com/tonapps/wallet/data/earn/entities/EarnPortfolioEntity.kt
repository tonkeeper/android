package com.tonapps.wallet.data.earn.entities

import java.math.BigDecimal

data class EarnPortfolioEntity(
    val totalUsd: BigDecimal,
    val positions: List<EarnPositionEntity>,
)