package com.tonapps.perps.screens.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonLabel
import ui.theme.Shapes
import ui.theme.UIKit

private val CardWidth = 100.dp
private val CardSpacing = 8.dp

@Composable
fun PerpsShelfRow(
    markets: List<PerpMarket>,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        items(items = markets, key = { it.marketIndex }) { market ->
            PerpsMarketCard(
                market = market,
                onClick = { onOpenPerpMarket(market.marketIndex, market.symbol) },
            )
        }
    }
}

@Composable
private fun PerpsMarketCard(
    market: PerpMarket,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(CardWidth)
            .clip(Shapes.medium)
            .background(UIKit.colorScheme.background.content)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PerpsAssetIcon(symbol = market.symbol, iconUrl = market.iconUrl, size = 44.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = market.symbol,
                style = UIKit.typography.body3,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (market.maxLeverage > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                MoonLabel(text = stringResource(Localization.perps_leverage_pattern, market.maxLeverage))
            }
        }
        Spacer(modifier = Modifier.height(1.dp))
        val change = market.priceChange24hPercent
        Text(
            text = change?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE,
            style = UIKit.typography.body3,
            color = perpsChangeColor(change),
            maxLines = 1,
        )
    }
}
