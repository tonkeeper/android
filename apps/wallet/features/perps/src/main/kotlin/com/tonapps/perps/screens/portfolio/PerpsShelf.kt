package com.tonapps.perps.screens.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import ui.components.moon.cell.MoonBundleTitleCell
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
fun PerpsShelf(
    title: String,
    markets: List<PerpMarket>,
    onOpenPerps: () -> Unit,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = title,
            onClick = onOpenPerps,
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .background(shape = Shapes.medium, color = UIKit.colorScheme.background.content)
                .padding(8.dp),
        ) {
            PerpsShelfGrid(markets = markets, onOpenPerpMarket = onOpenPerpMarket)
        }
    }
}

@Composable
private fun PerpsShelfGrid(
    markets: List<PerpMarket>,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        markets.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                rowItems.forEach { market ->
                    Box(modifier = Modifier.weight(1f)) {
                        PerpsShelfItem(
                            market = market,
                            onClick = { onOpenPerpMarket(market.marketIndex, market.symbol) },
                        )
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PerpsShelfItem(
    market: PerpMarket,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.medium12)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PerpsAssetIcon(symbol = market.symbol, iconUrl = market.iconUrl, size = 56.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = market.symbol,
                style = UIKit.typography.body3,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (market.maxLeverage > 0) {
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
