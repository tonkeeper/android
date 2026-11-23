package com.tonapps.perps.screens.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.markets.PerpsSideLabel
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonLabel
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.defaultBundleType
import ui.painterResource
import ui.theme.UIKit

private const val MaxVisiblePositions = 3
private const val MaxPreviewIcons = 2
private val IconSize = 24.dp
private val IconRing = 2.dp
private val IconOverlap = 8.dp

@Composable
fun PerpsPositionsCard(
    positions: List<PerpsPositionUi>,
    onOpenMarket: (marketIndex: Int, symbol: String) -> Unit,
    onOpenMore: () -> Unit,
) {
    val hidden = positions.drop(MaxVisiblePositions)
    val cellCount = positions.size.coerceAtMost(MaxVisiblePositions + 1)
    Column {
        positions.take(MaxVisiblePositions).forEachIndexed { index, item ->
            MoonBundleCell(
                position = defaultBundleType(cellCount, index),
                onClick = { onOpenMarket(item.position.marketIndex, item.position.symbol) },
            ) {
                PerpsPositionRow(item = item, modifier = Modifier.padding(16.dp))
            }
        }
        if (hidden.isNotEmpty()) {
            MoonBundleCell(
                position = MoonBundlePosition.Footer,
                onClick = onOpenMore,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(start = 16.dp - IconRing, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.padding(end = 12.dp + IconRing),
                        horizontalArrangement = Arrangement.spacedBy(-IconOverlap),
                    ) {
                        hidden.take(MaxPreviewIcons).forEach { item ->
                            Box(
                                modifier = Modifier
                                    .size(IconSize + IconRing * 2)
                                    .clip(CircleShape)
                                    .background(UIKit.colorScheme.background.content),
                                contentAlignment = Alignment.Center,
                            ) {
                                PerpsAssetIcon(symbol = item.position.symbol, iconUrl = item.position.iconUrl, size = IconSize)
                            }
                        }
                    }
                    Text(
                        text = stringResource(Localization.perps_more_positions),
                        style = UIKit.typography.label1,
                        color = UIKit.colorScheme.text.primary,
                    )
                    Icon(
                        modifier = Modifier.padding(start = 4.dp),
                        painter = painterResource(UIKitIcon.ic_chevron_right_16),
                        tint = UIKit.colorScheme.icon.secondary,
                        contentDescription = null,
                    )
                }
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
        Column(modifier = Modifier.weight(1f)) {
            Row(
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
            orderAutoCloseText(item)?.let { text ->
                Text(
                    text = text,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

@Composable
private fun orderAutoCloseText(item: PerpsOrderUi): String? {
    val takeProfit = item.takeProfitFormat
    val stopLoss = item.stopLossFormat

    return when {
        takeProfit != null && stopLoss != null -> stringResource(
            Localization.perps_limit_order_take_profit_stop_loss,
            takeProfit,
            stopLoss,
        )

        takeProfit != null -> stringResource(Localization.perps_limit_order_take_profit, takeProfit)
        stopLoss != null -> stringResource(Localization.perps_limit_order_stop_loss, stopLoss)
        else -> null
    }
}
