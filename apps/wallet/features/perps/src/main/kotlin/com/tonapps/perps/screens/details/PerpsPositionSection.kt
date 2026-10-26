package com.tonapps.perps.screens.details

import android.text.format.DateFormat
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PerpsAutoCloseAffordance
import com.tonapps.perps.data.PerpsAutoCloseLegKind
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.autoCloseAffordance
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.screens.markets.PerpsSideLabel
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.perps.screens.markets.perpsErrorDescription
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.theme.UIKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PerpsPositionSection(
    section: PerpsPositionSection,
    card: PerpsPositionCardUi?,
    flags: PerpsTradingFlags,
    canTrade: Boolean,
    onAction: (PerpsAssetDetailsAction) -> Unit,
    onShare: () -> Unit,
    onAdjustMargin: () -> Unit,
    onAutoClose: () -> Unit,
) {
    when (section) {
        is PerpsPositionSection.Open -> if (card != null) {
            PerpsOpenPositionCards(
                detail = section.detail,
                card = card,
                pnlPending = section.pnlPending,
                flags = flags,
                canTrade = canTrade,
                onShare = onShare,
                onAdjustMargin = onAdjustMargin,
                onAutoClose = onAutoClose,
            )
        }

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
    card: PerpsPositionCardUi?,
    flags: PerpsTradingFlags,
    canTrade: Boolean,
    onAutoClose: () -> Unit,
) {
    val detail = (section as? PerpsPositionSection.Open)?.detail
    val legs = listOfNotNull(card?.takeProfit, card?.stopLoss)
    val trailingAction = when (detail?.autoCloseAffordance(flags)) {
        PerpsAutoCloseAffordance.SET_TAKE_PROFIT -> Localization.perps_set_take_profit
        PerpsAutoCloseAffordance.SET_STOP_LOSS -> Localization.perps_set_stop_loss
        else -> null
    }
    val autoCloseEditable = canTrade && flags.autoCloseEnabled && detail?.autoCloseKnown == true
    val onLegClick = if (autoCloseEditable) {
        onAutoClose
    } else {
        null
    }

    val hasOrderRows = legs.isNotEmpty()

    if (hasOrderRows || trailingAction != null) {
        val legTitle = if (detail?.position?.side == PerpsPositionSide.SHORT) {
            stringResource(Localization.perps_close_short)
        } else {
            stringResource(Localization.perps_close_long)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            MoonBundleTitleCell(title = stringResource(Localization.perps_orders))
            if (hasOrderRows) {
                MoonBundleCell {
                    Column {
                        legs.forEachIndexed { index, leg ->
                            if (index > 0) {
                                MoonItemDivider()
                            }
                            PerpsAutoCloseLegRow(
                                leg = leg,
                                title = legTitle,
                                onClick = onLegClick,
                            )
                        }
                    }
                }
            }
            if (trailingAction != null) {
                if (hasOrderRows) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                MoonBundleCell {
                    PerpsAccentRow(
                        text = stringResource(trailingAction),
                        enabled = autoCloseEditable,
                        onClick = onAutoClose,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PerpsOpenPositionCards(
    detail: PerpsPositionDetail,
    card: PerpsPositionCardUi,
    pnlPending: Boolean,
    flags: PerpsTradingFlags,
    canTrade: Boolean,
    onShare: () -> Unit,
    onAdjustMargin: () -> Unit,
    onAutoClose: () -> Unit,
) {
    val showAdjustMargin = flags.addMarginEnabled || flags.removeMarginEnabled
    val showAutoClose =
        detail.autoCloseAffordance(flags) == PerpsAutoCloseAffordance.SET_AUTO_CLOSE

    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = stringResource(Localization.perps_your_position),
            content = {
                Row(
                    modifier = Modifier.clickable(onClick = onShare),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_share_16),
                        color = UIKit.colorScheme.text.accent,
                    )
                    Text(
                        text = stringResource(Localization.perps_share),
                        style = UIKit.typography.label2,
                        color = UIKit.colorScheme.text.accent,
                    )
                }
            },
        )
        MoonBundleCell {
            PerpsPositionPnlRow(position = detail.position, card = card, pnlPending = pnlPending)
        }
        Spacer(modifier = Modifier.height(8.dp))
        MoonBundleCell {
            PerpsPositionDetailRows(card = card)
        }
        if (showAdjustMargin || showAutoClose) {
            Spacer(modifier = Modifier.height(8.dp))
            MoonBundleCell {
                Column {
                    if (showAdjustMargin) {
                        PerpsAccentRow(
                            text = stringResource(Localization.perps_adjust_margin),
                            enabled = canTrade,
                            onClick = onAdjustMargin,
                        )
                    }
                    if (showAdjustMargin && showAutoClose) {
                        MoonItemDivider()
                    }
                    if (showAutoClose) {
                        PerpsAccentRow(
                            text = stringResource(Localization.perps_set_auto_close),
                            enabled = canTrade,
                            onClick = onAutoClose,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsPositionPnlRow(
    position: PerpsOpenPosition,
    card: PerpsPositionCardUi,
    pnlPending: Boolean,
) {
    val changeColor = perpsChangeColor(card.pnl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = card.equityFormat,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.primary,
            )
            if (pnlPending) {
                Text(
                    text = stringResource(Localization.perps_operation_pending),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = card.pnlFormat,
                        style = UIKit.typography.body2,
                        color = changeColor,
                    )
                    if (card.roiFormat != null) {
                        Text(
                            text = card.roiFormat,
                            style = UIKit.typography.body2,
                            color = changeColor.copy(alpha = 0.48f),
                        )
                    }
                }
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
private fun PerpsPositionDetailRows(card: PerpsPositionCardUi) {
    var sizeInToken by rememberSaveable { mutableStateOf(false) }
    val sizeText = if (sizeInToken) {
        card.sizeTokenFormat
    } else {
        card.sizeUsdFormat
    }
    val liquidationDistance = card.liquidationDistanceFormat?.let { distance ->
        stringResource(Localization.perps_liquidation_distance, distance)
    }

    Column {
        MoonPropertyBigCell(
            title = {
                MoonPropertyTitle(
                    title = stringResource(Localization.perps_size),
                    infoTooltip = stringResource(Localization.perps_size_tooltip),
                )
            },
            content = { MoonPropertyValue(title = sizeText) },
            valuePostfix = {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                    color = UIKit.colorScheme.icon.secondary,
                )
            },
            onClick = { sizeInToken = !sizeInToken },
        )
        MoonItemDivider()
        MoonPropertyBigCell(
            title = {
                MoonPropertyTitle(
                    title = stringResource(Localization.perps_liquidation_price),
                    subtitle = liquidationDistance,
                    infoTooltip = stringResource(Localization.perps_liquidation_price_tooltip),
                )
            },
            content = {
                MoonPropertyValue(title = card.liquidationPriceFormat)
            },
        )
        MoonItemDivider()
        PerpsTooltipPropertyCell(
            title = stringResource(Localization.perps_mark_price),
            tooltip = stringResource(Localization.perps_mark_price_tooltip),
            value = card.markPriceFormat,
        )
        MoonItemDivider()
        PerpsTooltipPropertyCell(
            title = stringResource(Localization.perps_entry_price),
            tooltip = stringResource(Localization.perps_entry_price_tooltip),
            value = card.entryPriceFormat,
        )
        MoonItemDivider()
        PerpsTooltipPropertyCell(
            title = stringResource(Localization.perps_funding),
            tooltip = stringResource(Localization.perps_funding_tooltip),
            value = card.fundingFormat,
        )
    }
}

@Composable
private fun PerpsTooltipPropertyCell(title: String, tooltip: String, value: String) {
    MoonPropertyBigCell(
        title = { MoonPropertyTitle(title = title, infoTooltip = tooltip) },
        content = { MoonPropertyValue(title = value) },
    )
}

@Composable
internal fun PerpsLimitOrdersSection(
    orders: List<PerpsLimitOrder>,
    priceDecimals: Int,
    enabled: Boolean,
    onOrderClick: (PerpsLimitOrder) -> Unit,
) {
    val locale = Locale.getDefault()
    val expiryFormat = remember(locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "d MMM"), locale)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.perps_limit_orders))
        orders.forEachIndexed { index, order ->
            MoonBundleCell(
                position = defaultBundleType(orders.size, index),
                onClick = if (enabled) {
                    { onOrderClick(order) }
                } else {
                    null
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = order.limitPrice
                                ?.let { formatUsdPrice(it.toDouble(), priceDecimals) }
                                ?: PERPS_EMPTY_VALUE,
                            style = UIKit.typography.h2,
                            color = UIKit.colorScheme.text.primary,
                        )
                        Text(
                            text = order.expiryUnixMs
                                ?.let {
                                    stringResource(
                                        Localization.perps_limit_price_expires,
                                        expiryFormat.format(Date(it)),
                                    )
                                }
                                ?: stringResource(Localization.perps_limit_price),
                            style = UIKit.typography.body2,
                            color = UIKit.colorScheme.text.secondary,
                        )
                    }
                    PerpsSideLabel(side = order.side)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsAutoCloseLegRow(
    leg: PerpsAutoCloseLegUi,
    title: String,
    onClick: (() -> Unit)?,
) {
    MoonPropertyBigCell(
        title = {
            MoonPropertyTitle(
                title = title,
                subtitle = stringResource(
                    Localization.perps_order_price,
                    leg.triggerPriceFormat,
                ),
                content = { PerpsLegKindLabel(kind = leg.kind) },
            )
        },
        content = {
            MoonItemTitle(text = leg.projectedPnlFormat)
        },
        contentDescription = {
            if (leg.projectedRoiFormat != null) {
                Text(
                    text = leg.projectedRoiFormat,
                    style = UIKit.typography.body2,
                    color = perpsChangeColor(leg.projectedRoi),
                )
            }
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
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = UIKit.typography.label1,
            color = if (enabled) {
                UIKit.colorScheme.text.accent
            } else {
                UIKit.colorScheme.text.secondary
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        MoonChevronRight()
    }
}
