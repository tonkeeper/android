package com.tonapps.perps.data

import java.math.BigDecimal
import java.math.RoundingMode

data class PerpMarket(
    val marketIndex: Int,
    val symbol: String,
    val name: String? = null,
    val iconUrl: String? = null,
    val maxLeverage: Int,
    val price: BigDecimal?,
    val priceChange24hPercent: BigDecimal?,
    val volume24h: BigDecimal?,
    val openInterestUsd: BigDecimal?,
    val fundingRateHourly: BigDecimal?,
    val priceDecimals: Int?,
    val sizeDecimals: Int?,
)

data class PerpsMarketDetails(
    val market: PerpMarket,
    val about: String?,
)

data class PerpsTradingState(
    val flags: PerpsTradingFlags,
    val limitOrders: List<PerpsLimitOrder>,
) {
    companion object {
        val PUBLIC = PerpsTradingState(flags = PerpsTradingFlags.ALL_ENABLED, limitOrders = emptyList())
    }
}

data class PerpsMarketsPage(
    val markets: List<PerpMarket>,
    val nextCursor: String?,
)

enum class PerpsMarketFilter {
    ALL,
    TOKENS,
    COMMODITIES,
    STOCKS,
    ETFS,
}

enum class PerpsSort {
    VOLUME,
    PRICE_CHANGE,
    OPEN_INTEREST,
}

fun PerpMarket.priceChange24hAbsolute(): BigDecimal? {
    val currentPrice = price ?: return null
    val percent = priceChange24hPercent ?: return null
    val factor = BigDecimal.ONE.add(percent.divide(BigDecimal(100), PERCENT_SCALE, RoundingMode.HALF_UP))
    if (factor.signum() == 0) {
        return null
    }
    return currentPrice.subtract(currentPrice.divide(factor, PERCENT_SCALE, RoundingMode.HALF_UP))
}

fun PerpMarket.withLivePrice(livePrice: BigDecimal?): PerpMarket {
    if (livePrice == null || livePrice.signum() <= 0) {
        return this
    }
    val snapshotPrice = price
    if (snapshotPrice != null && livePrice.compareTo(snapshotPrice) == 0) {
        return this
    }
    val percent = priceChange24hPercent ?: return copy(price = livePrice)
    val factor = BigDecimal.ONE.add(percent.divide(BigDecimal(100), PERCENT_SCALE, RoundingMode.HALF_UP))
    if (factor <= MIN_CHANGE_FACTOR || snapshotPrice == null || snapshotPrice.signum() <= 0) {
        return copy(price = livePrice)
    }
    val baseline = snapshotPrice.divide(factor, PERCENT_SCALE, RoundingMode.HALF_UP)
    if (baseline.signum() <= 0) {
        return copy(price = livePrice)
    }
    val livePercent = livePrice.divide(baseline, PERCENT_SCALE, RoundingMode.HALF_UP)
        .subtract(BigDecimal.ONE)
        .multiply(BigDecimal(100))
        .setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
    return copy(price = livePrice, priceChange24hPercent = livePercent)
}

private const val PERCENT_SCALE = 10
private val MIN_CHANGE_FACTOR = BigDecimal("0.01")
