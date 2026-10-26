package com.tonapps.perps.screens.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.markets.PerpsSideLabel
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonLabel
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.defaultBundleType
import ui.theme.UIKit

@Composable
fun PerpsPositionsCard(
    positions: List<PerpsPositionUi>,
    onOpenMarket: (marketIndex: Int, symbol: String) -> Unit,
) {
    Column {
        positions.forEachIndexed { index, item ->
            MoonBundleCell(
                position = defaultBundleType(positions.size, index),
                onClick = { onOpenMarket(item.position.marketIndex, item.position.symbol) },
            ) {
                PerpsPositionRow(item = item, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
internal fun PerpsPositionRow(
    item: PerpsPositionUi,
    modifier: Modifier = Modifier,
) {
    val position = item.position
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerpsAssetIcon(symbol = position.symbol, iconUrl = position.iconUrl)
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = position.symbol,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            position.leverage?.let { leverage ->
                MoonLabel(text = stringResource(Localization.perps_leverage_pattern, leverage))
            }
            PerpsSideLabel(side = position.side)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = item.equityFormat,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
            )
            Text(
                text = item.pnlFormat,
                style = UIKit.typography.body2,
                color = perpsChangeColor(item.pnl),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun PerpsPortfolioOrderRow(
    item: PerpsOrderUi,
    modifier: Modifier = Modifier,
) {
    val order = item.order
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerpsAssetIcon(symbol = order.symbol, iconUrl = order.iconUrl)
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = order.symbol,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            order.leverage?.let { leverage ->
                MoonLabel(text = stringResource(Localization.perps_leverage_pattern, leverage))
            }
            PerpsSideLabel(side = order.side)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = item.amountFormat,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
            )
            Text(
                text = stringResource(Localization.perps_limit_order_subtitle, item.limitPriceFormat),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
                maxLines = 1,
            )
        }
    }
}
