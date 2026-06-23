package com.tonapps.trading.screens.assets

import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.Formatter
import io.tradingapi.models.MarketItem
import java.math.BigDecimal

data class AssetItem(
    val id: String,
    val symbol: String,
    val name: String,
    val imageUrl: String,
    val formattedPrice: String,
    val formattedChange: String,
)

fun MarketItem.toAssetItem(currencyCode: String): AssetItem {
    return AssetItem(
        id = asset.id,
        symbol = asset.symbol,
        name = asset.name,
        imageUrl = asset.imageUrl,
        formattedPrice = CurrencyFormatter.formatFiat(
            currency = currencyCode,
            value = BigDecimal(metrics.price),
            stripTrailingZeros = true,
        ).toString(),
        formattedChange = Formatter.percent(metrics.change24hPercent),
    )
}
