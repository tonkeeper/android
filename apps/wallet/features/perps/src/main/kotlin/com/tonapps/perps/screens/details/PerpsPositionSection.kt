package com.tonapps.perps.screens.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsAutoCloseAffordance
import com.tonapps.perps.data.PerpsAutoCloseLeg
import com.tonapps.perps.data.PerpsAutoCloseLegKind
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.autoCloseAffordance
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatSignedUsd
import com.tonapps.perps.data.formatTokenAmount
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.screens.markets.PerpsSideLabel
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.perps.screens.markets.perpsErrorDescription
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.theme.UIKit

@Composable
internal fun PerpsPositionSection(
    section: PerpsPositionSection,
    flags: PerpsTradingFlags,
    symbol: String,
    sizeDecimals: Int?,
    onAction: (PerpsAssetDetailsAction) -> Unit,
    onComingSoon: () -> Unit,
) {
    when (section) {
        is PerpsPositionSection.Open -> PerpsOpenPositionCards(
            detail = section.detail,
            flags = flags,
            symbol = symbol,
            sizeDecimals = sizeDecimals,
            onComingSoon = onComingSoon,
        )

        is PerpsPositionSection.Failed -> PerpsPositionFailedCard(
            error = section.error,
            onRetry = { onAction(PerpsAssetDetailsAction.Retry) },
        )

        PerpsPositionSection.Hidden,
        PerpsPositionSection.Loading -> Unit
    }
}

@Composable
internal fun PerpsOrdersSection(
    section: PerpsPositionSection,
    flags: PerpsTradingFlags,
    limitOrders: List<PerpsLimitOrder>,
    symbol: String,
    sizeDecimals: Int?,
    onComingSoon: () -> Unit,
) {
    val detail = (section as? PerpsPositionSection.Open)?.detail
    val legs = listOfNotNull(detail?.takeProfit, detail?.stopLoss)
    val trailingAction = when (detail?.autoCloseAffordance(flags)) {
        PerpsAutoCloseAffordance.SET_TAKE_PROFIT -> Localization.perps_set_take_profit
        PerpsAutoCloseAffordance.SET_STOP_LOSS -> Localization.perps_set_stop_loss
        else -> null
    }

    if (limitOrders.isNotEmpty() || legs.isNotEmpty() || trailingAction != null) {
        val legAction = if (detail?.position?.side == PerpsPositionSide.SHORT) {
            stringResource(Localization.perps_buy)
        } else {
            stringResource(Localization.perps_sell)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            MoonBundleTitleCell(title = stringResource(Localization.perps_orders))
            MoonBundleCell {
                Column {
                    limitOrders.forEachIndexed { index, order ->
                        if (index > 0) {
                            MoonDivider()
                        }
                        PerpsLimitOrderRow(
                            order = order,
                            symbol = symbol,
                            sizeDecimals = sizeDecimals,
                            onClick = onComingSoon,
                        )
                    }
                    legs.forEachIndexed { index, leg ->
                        if (index > 0 || limitOrders.isNotEmpty()) {
                            MoonDivider()
                        }
                        PerpsAutoCloseLegRow(
                            leg = leg,
                            action = legAction,
                            onClick = onComingSoon,
                        )
                    }
                    if (trailingAction != null) {
                        if (limitOrders.isNotEmpty() || legs.isNotEmpty()) {
                            MoonDivider()
                        }
                        PerpsAccentRow(
                            text = stringResource(trailingAction),
                            onClick = onComingSoon,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PerpsOpenPositionCards(
    detail: PerpsPositionDetail,
    flags: PerpsTradingFlags,
    symbol: String,
    sizeDecimals: Int?,
    onComingSoon: () -> Unit,
) {
    val showAdjustMargin = flags.addMarginEnabled || flags.removeMarginEnabled
    val showAutoClose =
        detail.autoCloseAffordance(flags) == PerpsAutoCloseAffordance.SET_AUTO_CLOSE

    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = stringResource(Localization.perps_your_position),
            content = {
                Row(
                    modifier = Modifier.clickable(onClick = onComingSoon),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Localization.perps_share),
                        style = UIKit.typography.label2,
                        color = UIKit.colorScheme.text.accent,
                    )
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_share_16),
                        color = UIKit.colorScheme.text.accent,
                    )
                }
            },
        )
        MoonBundleCell {
            PerpsPositionPnlRow(position = detail.position)
        }
        Spacer(modifier = Modifier.height(12.dp))
        MoonBundleCell {
            PerpsPositionDetailRows(
                position = detail.position,
                symbol = symbol,
                sizeDecimals = sizeDecimals,
            )
        }
        if (showAdjustMargin || showAutoClose) {
            Spacer(modifier = Modifier.height(12.dp))
            MoonBundleCell {
                Column {
                    if (showAdjustMargin) {
                        PerpsAccentRow(
                            text = stringResource(Localization.perps_adjust_margin),
                            onClick = onComingSoon,
                        )
                    }
                    if (showAdjustMargin && showAutoClose) {
                        MoonDivider()
                    }
                    if (showAutoClose) {
                        PerpsAccentRow(
                            text = stringResource(Localization.perps_set_auto_close),
                            onClick = onComingSoon,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsPositionPnlRow(position: PerpsOpenPosition) {
    val changeColor = perpsChangeColor(position.unrealizedPnl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = position.margin?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = position.unrealizedPnl?.let { formatSignedUsd(it) } ?: PERPS_EMPTY_VALUE,
                    style = UIKit.typography.body2,
                    color = changeColor,
                )
                Text(
                    text = position.roiPct?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE,
                    style = UIKit.typography.body2,
                    color = changeColor.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            position.leverage?.let { leverage ->
                MoonLabel(text = stringResource(Localization.perps_leverage_pattern, leverage))
            }
            PerpsSideLabel(side = position.side)
        }
    }
}

@Composable
private fun PerpsPositionDetailRows(
    position: PerpsOpenPosition,
    symbol: String,
    sizeDecimals: Int?,
) {
    var sizeInToken by rememberSaveable { mutableStateOf(false) }
    val sizeText = if (sizeInToken) {
        position.size?.let { formatTokenAmount(it, symbol, sizeDecimals) }
    } else {
        position.positionValue?.let { formatUsd(it) }
    }
    val liquidationDistance = position.liquidationDistancePct?.let { distance ->
        stringResource(Localization.perps_liquidation_distance, formatSignedPercent(distance))
    }

    Column {
        MoonPropertyBigCell(
            title = { MoonPropertyTitle(title = stringResource(Localization.perps_size)) },
            content = { MoonPropertyValue(title = sizeText ?: PERPS_EMPTY_VALUE) },
            valuePostfix = {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                    color = UIKit.colorScheme.icon.secondary,
                )
            },
            onClick = { sizeInToken = !sizeInToken },
        )
        MoonDivider()
        MoonPropertyBigCell(
            title = {
                MoonPropertyTitle(
                    title = stringResource(Localization.perps_liquidation_price),
                    subtitle = liquidationDistance,
                )
            },
            content = {
                MoonPropertyValue(
                    title = position.liquidationPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
                )
            },
        )
        MoonDivider()
        MoonPropertyBigCell(
            title = stringResource(Localization.perps_mark_price),
            value = position.markPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
            valueDescription = null,
        )
        MoonDivider()
        MoonPropertyBigCell(
            title = stringResource(Localization.perps_entry_price),
            value = position.avgEntryPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
            valueDescription = null,
        )
        MoonDivider()
        MoonPropertyBigCell(
            title = stringResource(Localization.perps_funding),
            value = position.fundingPaid?.let { formatSignedUsd(it) } ?: PERPS_EMPTY_VALUE,
            valueDescription = null,
        )
    }
}

@Composable
private fun PerpsLimitOrderRow(
    order: PerpsLimitOrder,
    symbol: String,
    sizeDecimals: Int?,
    onClick: () -> Unit,
) {
    val action = if (order.side == PerpsPositionSide.LONG) {
        stringResource(Localization.perps_buy)
    } else {
        stringResource(Localization.perps_sell)
    }
    val title = order.remainingBase?.let { remaining ->
        stringResource(
            Localization.perps_order_amount_pattern,
            action,
            formatTokenAmount(remaining, symbol, sizeDecimals),
        )
    } ?: action
    val total = order.remainingBase?.let { remaining ->
        order.limitPrice?.let { price -> formatUsd(remaining.multiply(price)) }
    }

    MoonPropertyBigCell(
        title = {
            MoonPropertyTitle(
                title = title,
                subtitle = stringResource(
                    Localization.perps_order_price,
                    order.limitPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
                ),
            )
        },
        content = { MoonPropertyValue(title = total ?: PERPS_EMPTY_VALUE) },
        onClick = onClick,
    )
}

@Composable
private fun PerpsAutoCloseLegRow(
    leg: PerpsAutoCloseLeg,
    action: String,
    onClick: () -> Unit,
) {
    val title = leg.sharePct?.let { share ->
        stringResource(Localization.perps_order_share_pattern, action, formatPlainDecimal(share))
    } ?: action

    MoonPropertyBigCell(
        title = {
            MoonPropertyTitle(
                title = title,
                subtitle = stringResource(
                    Localization.perps_order_price,
                    leg.triggerPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
                ),
                content = { PerpsLegKindLabel(kind = leg.kind) },
            )
        },
        content = {
            MoonItemTitle(
                text = leg.projectedEquity?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
            )
        },
        contentDescription = {
            Text(
                text = leg.projectedRoiPct?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE,
                style = UIKit.typography.body2,
                color = perpsChangeColor(leg.projectedRoiPct),
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun PerpsLegKindLabel(kind: PerpsAutoCloseLegKind) {
    if (kind == PerpsAutoCloseLegKind.TAKE_PROFIT) {
        MoonLabel(
            text = stringResource(Localization.perps_take_profit_short),
            colors = MoonLabelDefault.success(),
        )
    } else {
        MoonLabel(
            text = stringResource(Localization.perps_stop_loss_short),
            colors = MoonLabelDefault.error(),
        )
    }
}

@Composable
private fun PerpsPositionFailedCard(
    error: PerpsError,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.perps_your_position))
        MoonBundleCell {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Localization.perps_position_load_failed),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                )
                perpsErrorDescription(error)?.let { description ->
                    Text(
                        text = description,
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.secondary,
                    )
                }
                Text(
                    modifier = Modifier.clickable(onClick = onRetry),
                    text = stringResource(Localization.perps_retry),
                    style = UIKit.typography.label2,
                    color = UIKit.colorScheme.text.accent,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsAccentRow(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = UIKit.typography.label1,
            color = UIKit.colorScheme.text.accent,
        )
        Spacer(modifier = Modifier.weight(1f))
        MoonChevronRight()
    }
}
