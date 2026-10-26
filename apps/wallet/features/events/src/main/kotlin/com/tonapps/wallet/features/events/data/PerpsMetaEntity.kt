package com.tonapps.wallet.features.events.data

import com.tonapps.icu.Coins
import java.math.BigDecimal

enum class PerpsSide { Long, Short }

enum class PerpsCloseReason { Manual, TakeProfit, StopLoss, Liquidation }

data class PerpsMetaEntity(
    val symbol: String?,
    val side: PerpsSide?,
    val reason: PerpsCloseReason?,
    val marketIndex: Int? = null,
    val leverage: Int? = null,
    val entryPrice: BigDecimal? = null,
    val closePrice: BigDecimal? = null,
    val size: BigDecimal? = null,
    val sizeUsd: BigDecimal? = null,
    val resultPct: BigDecimal? = null,
    val marginUsd: BigDecimal? = null,
    val fundingUsd: BigDecimal? = null,
)

sealed interface PerpsEventKind {
    data class Opened(val side: PerpsSide?) : PerpsEventKind
    data class Increased(val side: PerpsSide?) : PerpsEventKind
    data class Reduced(val side: PerpsSide?) : PerpsEventKind
    data class Closed(val side: PerpsSide?) : PerpsEventKind
    data class Liquidated(val side: PerpsSide?) : PerpsEventKind
    data object TakeProfit : PerpsEventKind
    data object StopLoss : PerpsEventKind
    data object Deposit : PerpsEventKind
    data object Withdrawal : PerpsEventKind
}

data class PerpsResultEntity(
    val value: Coins,
    val symbol: String,
    val isPositive: Boolean,
) {
    val isDollar: Boolean
        get() = symbol.equals("USD", ignoreCase = true) ||
            symbol.equals("USDC", ignoreCase = true) ||
            symbol.equals("USDT", ignoreCase = true)
}
