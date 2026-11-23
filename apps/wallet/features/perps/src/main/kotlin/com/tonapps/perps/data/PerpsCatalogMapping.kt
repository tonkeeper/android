package com.tonapps.perps.data

import com.tonapps.core.components.withLocaleDecimalSeparator
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetMarketCapEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.multichain.asset.AssetWithDetails
import com.tonapps.wallet.data.multichain.asset.perpsMarketIndexOrNull
import io.tradingapi.models.AssetRef
import io.tradingapi.models.AssetType
import io.tradingapi.models.MarketItem
import java.math.BigDecimal
import java.math.RoundingMode

internal fun MarketItem.isListedIn(signedSymbols: Map<Int, String>?): Boolean {
    if (asset.assetType != AssetType.perpetuals || signedSymbols == null) {
        return true
    }
    val index = perpsMarketIndexOrNull(asset.id) ?: return false
    return signedSymbols[index] == asset.symbol
}

internal fun AssetRef.isListedIn(signedSymbols: Map<Int, String>?, marketIndex: Int): Boolean {
    val perp = perps ?: return false
    if (perp.marketIndex != marketIndex) {
        return false
    }
    val catalogSymbol = perp.symbol.takeIf { it.isNotBlank() } ?: symbol
    return signedSymbols == null || signedSymbols[marketIndex] == catalogSymbol
}

fun MarketItem.toPerpMarket(): PerpMarket? = perpMarket(
    assetId = asset.id,
    symbol = asset.symbol,
    name = asset.name,
    image = asset.imageUrl,
    leverage = asset.leverage ?: 0,
    price = metrics.price.toWireDecimalOrNull(),
    change = metrics.change24hPercent,
    volume = metrics.volume.toWireDecimalOrNull(),
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
        verification = AssetEntity.Verification.entries.firstOrNull { it.name == asset.verification.value }
            ?: AssetEntity.Verification.none,
    )
    entity.valueOrNull ?: return null
    val change = metrics.change24hPercent.toWireDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    val plainPercent = when (change?.signum()) {
        null -> "0.00%"
        1 -> "+${change.toPlainString()}%"
        else -> "${change.toPlainString()}%"
    }
    val percent = plainPercent.withLocaleDecimalSeparator()
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
                .takeIf { it.toWireDecimalOrNull()?.signum() == 1 }
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
        price = perp.markPrice.toWireDecimalOrNull()
            ?: perp.lastPrice.toWireDecimalOrNull()
            ?: perp.indexPrice.toWireDecimalOrNull(),
        priceChange24hPercent = perp.priceChange24h.toWireDecimalOrNull(),
        volume24h = perp.volume24hUsd.toWireDecimalOrNull(),
        openInterestUsd = perp.openInterestUsd.toWireDecimalOrNull(),
        fundingRateHourly = perp.fundingRateHourly?.toWireDecimalOrNull(),
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
    val index = perpsMarketIndexOrNull(assetId) ?: return null
    val ticker = symbol.takeIf { it.isNotBlank() }?.uppercase() ?: return null

    return PerpMarket(
        marketIndex = index,
        symbol = ticker,
        name = name.takeIf { it.isNotBlank() },
        iconUrl = image.takeIf { it.isNotBlank() },
        maxLeverage = leverage,
        price = price,
        priceChange24hPercent = change?.removeSuffix("%")?.toWireDecimalOrNull(),
        volume24h = volume,
        openInterestUsd = null,
        fundingRateHourly = null,
        priceDecimals = null,
        sizeDecimals = null,
    )
}
