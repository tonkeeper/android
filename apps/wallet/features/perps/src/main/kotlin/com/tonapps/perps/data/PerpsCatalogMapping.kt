package com.tonapps.perps.data

import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetMarketCapEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.multichain.asset.AssetWithDetails
import com.tonapps.wallet.data.multichain.asset.PERP_ASSET_ID_PREFIX
import io.tradingapi.models.AssetRef
import io.tradingapi.models.AssetType
import io.tradingapi.models.MarketItem
import java.math.BigDecimal
import java.math.RoundingMode

fun MarketItem.toPerpMarket(): PerpMarket? = perpMarket(
    assetId = asset.id,
    symbol = asset.symbol,
    name = asset.name,
    image = asset.imageUrl,
    leverage = asset.leverage ?: 0,
    price = metrics.price.toBigDecimalOrNull(),
    change = metrics.change24hPercent,
    volume = metrics.volume.toBigDecimalOrNull(),
)

fun MarketItem.toCatalogSearchRow(currency: String): CatalogSearchRow? {
    if (asset.assetType == AssetType.perpetuals) {
        return toPerpMarket()?.let { CatalogSearchRow.Perp(it, currency) }
    }
    val entity = AssetEntity(
        id = asset.id,
        name = asset.name,
        symbol = asset.symbol,
        decimals = asset.decimals,
        imageUrl = asset.imageUrl,
        verification = AssetEntity.Verification.valueOf(asset.verification.value),
    )
    entity.valueOrNull ?: return null
    val change = metrics.change24hPercent.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    val percent = when (change?.signum()) {
        null -> "0.00%"
        1 -> "+${change.toPlainString()}%"
        else -> "${change.toPlainString()}%"
    }
    return CatalogSearchRow.Spot(
        AssetWithDetails(
            asset = entity,
            rate = AssetRateEntity(
                price = metrics.price,
                percentChange24h = percent,
                currencyCode = currency,
            ),
            marketCap = metrics.marketCap?.let { AssetMarketCapEntity(marketCap = it, currencyCode = currency) },
            volume = metrics.volume
                .takeIf { it.toBigDecimalOrNull()?.signum() == 1 }
                ?.let { AssetMarketCapEntity(marketCap = it, currencyCode = currency) },
        ),
    )
}

fun AssetRef.toPerpMarket(): PerpMarket? {
    val perp = perps ?: return null
    val ticker = (perp.symbol.takeIf { it.isNotBlank() } ?: symbol)
        .takeIf { it.isNotBlank() }
        ?.uppercase()
        ?: return null

    return PerpMarket(
        marketIndex = perp.marketIndex,
        symbol = ticker,
        name = name.takeIf { it.isNotBlank() },
        iconUrl = imageUrl.takeIf { it.isNotBlank() },
        maxLeverage = perp.maxLeverage,
        price = perp.markPrice.toBigDecimalOrNull()
            ?: perp.lastPrice.toBigDecimalOrNull()
            ?: perp.indexPrice.toBigDecimalOrNull(),
        priceChange24hPercent = perp.priceChange24h.toBigDecimalOrNull(),
        volume24h = perp.volume24hUsd.toBigDecimalOrNull(),
        openInterestUsd = perp.openInterestUsd.toBigDecimalOrNull(),
        fundingRateHourly = perp.fundingRateHourly?.toBigDecimalOrNull(),
        priceDecimals = perp.priceDecimals,
        sizeDecimals = perp.sizeDecimals,
    )
}

private fun perpMarket(
    assetId: String,
    symbol: String,
    name: String,
    image: String,
    leverage: Int,
    price: BigDecimal?,
    change: String?,
    volume: BigDecimal?,
): PerpMarket? {
    if (!assetId.startsWith(PERP_ASSET_ID_PREFIX)) {
        return null
    }
    val index = assetId.removePrefix(PERP_ASSET_ID_PREFIX).toIntOrNull() ?: return null
    val ticker = symbol.takeIf { it.isNotBlank() }?.uppercase() ?: return null

    return PerpMarket(
        marketIndex = index,
        symbol = ticker,
        name = name.takeIf { it.isNotBlank() },
        iconUrl = image.takeIf { it.isNotBlank() },
        maxLeverage = leverage,
        price = price,
        priceChange24hPercent = change?.removeSuffix("%")?.toBigDecimalOrNull(),
        volume24h = volume,
        openInterestUsd = null,
        fundingRateHourly = null,
        priceDecimals = null,
        sizeDecimals = null,
    )
}
