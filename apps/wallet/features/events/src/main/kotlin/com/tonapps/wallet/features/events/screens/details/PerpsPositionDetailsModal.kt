package com.tonapps.wallet.features.events.screens.details

import com.tonapps.wallet.data.core.HIDDEN_BALANCE
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.screens.details.PerpsShareCard
import com.tonapps.perps.screens.details.PerpsSharePositionDialog
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.markets.PerpsSideLabel
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.PerpsCloseReason
import com.tonapps.wallet.features.events.data.PerpsEventKind
import com.tonapps.wallet.features.events.data.PerpsMetaEntity
import com.tonapps.wallet.features.events.data.PerpsSide
import com.tonapps.wallet.features.events.perpsResultText
import com.tonapps.wallet.features.events.perpsSubtitle
import com.tonapps.wallet.features.events.title
import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import java.math.BigDecimal
import java.time.OffsetDateTime
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonLabel
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

internal val HistoryEventEntity.isPerpsPositionEvent: Boolean
    get() = when (perpsKind) {
        is PerpsEventKind.Opened, is PerpsEventKind.Increased, is PerpsEventKind.Reduced,
        is PerpsEventKind.Closed, is PerpsEventKind.Liquidated,
        PerpsEventKind.TakeProfit, PerpsEventKind.StopLoss -> true
        PerpsEventKind.Deposit, PerpsEventKind.Withdrawal, null -> false
    }

private val HistoryEventEntity.isPerpsClosingEvent: Boolean
    get() = when (perpsKind) {
        is PerpsEventKind.Reduced, is PerpsEventKind.Closed, is PerpsEventKind.Liquidated,
        PerpsEventKind.TakeProfit, PerpsEventKind.StopLoss -> true
        is PerpsEventKind.Opened, is PerpsEventKind.Increased,
        PerpsEventKind.Deposit, PerpsEventKind.Withdrawal, null -> false
    }

private val HistoryEventEntity.isPerpsFullCloseEvent: Boolean
    get() = activityType == ActivityType.perpsPeriodPosition_closed

@Composable
internal fun PerpsPositionDetailsModal(
    event: HistoryEventEntity,
    market: PerpMarket?,
    hiddenBalance: Boolean,
    onDismiss: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onDismiss)
    val rows = remember(event, hiddenBalance) { event.perpsDetailsRows(hiddenBalance) }
    val shareCard = remember(event, market) { event.shareCardOrNull(market) }
    var sharing by remember { mutableStateOf(false) }

    MoonModalDialog(navigator = navigator) {
        MoonActionIcon(
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 16.dp, end = 16.dp),
            painter = painterResource(UIKitIcon.ic_close_16),
            onClick = { navigator.close() },
            tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
            contentDescription = stringResource(Localization.close),
        )

        PerpsPositionHeader(
            event = event,
            iconUrl = market?.iconUrl,
            hiddenBalance = hiddenBalance,
        )

        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            PerpsPositionRows(rows = rows)
        }

        if (shareCard != null) {
            Spacer(Modifier.height(16.dp))
            MoonAccentButton(
                text = stringResource(Localization.share),
                size = ButtonSizeSmall,
                buttonColors = ButtonColorsSecondary,
                icon = {
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_share_16),
                        size = 16.dp,
                        color = UIKit.colorScheme.buttonSecondary.primaryForeground,
                    )
                },
                onClick = { sharing = true },
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (sharing && shareCard != null) {
        PerpsSharePositionDialog(
            card = shareCard,
            onShared = {},
            onClose = { sharing = false },
        )
    }
}

@Composable
private fun PerpsPositionHeader(
    event: HistoryEventEntity,
    iconUrl: String?,
    hiddenBalance: Boolean,
) {
    val meta = event.perps
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val symbol = meta?.symbol
        if (symbol != null) {
            PerpsAssetIcon(
                symbol = symbol,
                iconUrl = iconUrl,
                size = 96.dp,
            )
            Spacer(Modifier.height(20.dp))
        }

        Text(
            text = event.perpsHeadline(hiddenBalance) ?: event.perpsSubtitle() ?: event.title(),
            style = UIKit.typography.h2,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                Localization.history_perps_event_on,
                event.perpsActionTitle(),
                event.blockTime.formatFullDateTime(),
            ),
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
            textAlign = TextAlign.Center,
        )

        val leverage = meta?.leverage
        val side = meta?.side?.toPositionSide()
        if (leverage != null || side != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leverage != null) {
                    MoonLabel(text = stringResource(Localization.perps_leverage_pattern, leverage))
                }
                if (side != null) {
                    PerpsSideLabel(side = side)
                }
            }
        }
    }
}

@Composable
private fun PerpsPositionRows(rows: List<PerpsDetailsRow>) {
    MoonBundleCell {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    MoonItemDivider()
                }
                MoonPropertyBigCell(
                    title = stringResource(row.titleRes),
                    value = row.value,
                    valueDescription = row.hint?.let {
                        stringResource(Localization.perps_review_base_pattern, it.amount, it.symbol)
                    },
                )
            }
        }
    }
}

private data class PerpsDetailsRow(
    val titleRes: Int,
    val value: String,
    val hint: PerpsAmountHint? = null,
)

private data class PerpsAmountHint(
    val amount: String,
    val symbol: String,
)

private fun HistoryEventEntity.perpsDetailsRows(hiddenBalance: Boolean): List<PerpsDetailsRow> {
    val meta = perps
    val symbol = meta?.symbol
    val closing = isPerpsClosingEvent
    val fee = feeDecimalOrNull()
    return buildList {
        meta?.entryPrice?.let {
            add(PerpsDetailsRow(Localization.perps_entry_price, formatUsd(it)))
        }
        if (closing) {
            meta?.closePrice?.let {
                add(PerpsDetailsRow(Localization.perps_close_price, formatUsd(it)))
            }
        }
        if (closing || perpsKind is PerpsEventKind.Increased) {
            meta?.sizeUsd?.let { sizeUsd ->
                if (hiddenBalance) {
                    add(PerpsDetailsRow(Localization.perps_size, HIDDEN_BALANCE))
                } else {
                    val sizeHint = meta.size?.let { size ->
                        symbol?.let { PerpsAmountHint(amount = formatPlainDecimal(size), symbol = it) }
                    }
                    add(PerpsDetailsRow(Localization.perps_size, formatUsdAmount(sizeUsd), sizeHint))
                }
            }
        }
        if (closing) {
            meta?.resultPct?.takeUnless { hiddenBalance }?.let {
                val title = if (it.signum() < 0) {
                    Localization.perps_loss
                } else {
                    Localization.perps_profit
                }
                add(PerpsDetailsRow(title, formatSignedPercent(it)))
            }
        }
        fee?.let { value ->
            val feeText = if (hiddenBalance) {
                HIDDEN_BALANCE
            } else {
                formatUsdAmount(value)
            }
            add(PerpsDetailsRow(Localization.fee, feeText))
        }
        if (isPerpsFullCloseEvent) {
            meta?.fundingUsd?.let {
                val fundingText = if (hiddenBalance) {
                    HIDDEN_BALANCE
                } else {
                    formatSignedUsdAmount(it)
                }
                add(PerpsDetailsRow(Localization.perps_funding, fundingText))
            }
        }
    }
}

@Composable
private fun HistoryEventEntity.perpsHeadline(hiddenBalance: Boolean): String? {
    if (isPerpsOpenOrIncrease) {
        val margin = perps?.marginUsd ?: return null
        return if (hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            remember(margin) { formatUsdAmount(margin) }
        }
    }
    val result = perpsResult ?: return null
    val text = perpsResultText() ?: return null
    if (hiddenBalance) {
        return HIDDEN_BALANCE
    }
    val sign = if (result.isPositive) {
        CurrencyFormatter.PREFIX_PLUS
    } else {
        CurrencyFormatter.PREFIX_MINUS
    }
    return sign + text
}

@Composable
private fun HistoryEventEntity.perpsActionTitle(): String = when (perpsKind) {
    PerpsEventKind.TakeProfit -> stringResource(Localization.history_perps_take_profit_executed)
    PerpsEventKind.StopLoss -> stringResource(Localization.history_perps_stop_loss_executed)
    is PerpsEventKind.Opened, is PerpsEventKind.Increased, is PerpsEventKind.Reduced,
    is PerpsEventKind.Closed, is PerpsEventKind.Liquidated,
    PerpsEventKind.Deposit, PerpsEventKind.Withdrawal, null -> title()
}

private fun HistoryEventEntity.feeDecimalOrNull(): BigDecimal? {
    val amount = feeAmount?.toBigDecimalOrNull() ?: return null
    val decimals = feeAsset?.decimals ?: return null
    return amount.movePointLeft(decimals)
}

private fun HistoryEventEntity.shareCardOrNull(market: PerpMarket?): PerpsShareCard? {
    if (!isPerpsClosingEvent) {
        return null
    }
    val meta = perps ?: return null
    val symbol = meta.symbol ?: return null
    val side = meta.side?.toPositionSide() ?: return null
    val resultPct = meta.resultPct ?: return null
    val entryPrice = meta.entryPrice ?: return null
    val closePrice = meta.closePrice ?: return null
    return PerpsShareCard(
        name = market?.name ?: symbol,
        symbol = symbol,
        iconUrl = market?.iconUrl,
        side = side,
        leverage = meta.leverage,
        roiPct = resultPct,
        isProfit = resultPct.signum() >= 0,
        entryPrice = entryPrice,
        currentPrice = null,
        date = blockTime.toInstant().toEpochMilli(),
        closePrice = closePrice,
    )
}

private fun PerpsSide.toPositionSide(): PerpsPositionSide = when (this) {
    PerpsSide.Long -> PerpsPositionSide.LONG
    PerpsSide.Short -> PerpsPositionSide.SHORT
}

private val previewUsdc = AssetEntity(
    id = "lighter/mainnet/3",
    name = "USDC",
    symbol = "USDC",
    decimals = 6,
    imageUrl = "",
)

private fun previewPerpsEvent(
    type: ActivityType,
    meta: PerpsMetaEntity,
    inAmount: String? = null,
    outAmount: String? = null,
) = HistoryEventEntity(
    activityType = type,
    status = ActivityStatus.confirmed,
    blockTime = OffsetDateTime.now(),
    walletAddress = null,
    fromAddress = null,
    toAddress = null,
    fromChain = null,
    toChain = null,
    inAmount = inAmount,
    outAmount = outAmount,
    inAmountUsd = null,
    outAmountUsd = null,
    feeAmount = "144000",
    feeAmountUsd = null,
    inAsset = inAmount?.let { previewUsdc },
    outAsset = outAmount?.let { previewUsdc },
    feeAsset = previewUsdc,
    txIds = listOf("lighter:a818e3159db2bf80c2676e6e9fd2bd46"),
    blockNumber = null,
    protocol = "lighter",
    explorerUrl = null,
    comment = null,
    direction = ActivityDirection.self,
    perps = meta,
)

@Preview
@Composable
private fun PerpsPositionOpenedPreview() {
    ThemedPreview {
        PerpsPositionDetailsModal(
            hiddenBalance = false,
            event = previewPerpsEvent(
                type = ActivityType.perpsPeriodPosition_opened,
                meta = PerpsMetaEntity(
                    symbol = "BTC",
                    side = PerpsSide.Long,
                    reason = null,
                    marketIndex = 1,
                    leverage = 40,
                    entryPrice = BigDecimal("66541.70"),
                    size = BigDecimal("0.00781"),
                    sizeUsd = BigDecimal("540.00"),
                    marginUsd = BigDecimal("20.50"),
                ),
            ),
            market = null,
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsPositionIncreasedPreview() {
    ThemedPreview {
        PerpsPositionDetailsModal(
            event = previewPerpsEvent(
                type = ActivityType.perpsPeriodPosition_increased,
                meta = PerpsMetaEntity(
                    symbol = "BTC",
                    side = PerpsSide.Long,
                    reason = null,
                    marketIndex = 1,
                    leverage = 40,
                    entryPrice = BigDecimal("66912.40"),
                    size = BigDecimal("0.00299"),
                    sizeUsd = BigDecimal("200.00"),
                    marginUsd = BigDecimal("5.00"),
                ),
            ),
            market = null,
            hiddenBalance = false,
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsPositionReducedPreview() {
    ThemedPreview {
        PerpsPositionDetailsModal(
            event = previewPerpsEvent(
                type = ActivityType.perpsPeriodPosition_reduced,
                meta = PerpsMetaEntity(
                    symbol = "BTC",
                    side = PerpsSide.Long,
                    reason = PerpsCloseReason.Manual,
                    marketIndex = 1,
                    leverage = 40,
                    entryPrice = BigDecimal("66541.70"),
                    closePrice = BigDecimal("67210.10"),
                    size = BigDecimal("0.0039"),
                    sizeUsd = BigDecimal("262.12"),
                    resultPct = BigDecimal("40.18"),
                ),
                inAmount = "2610000",
            ),
            market = null,
            hiddenBalance = false,
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsPositionClosedPreview() {
    ThemedPreview {
        PerpsPositionDetailsModal(
            hiddenBalance = false,
            event = previewPerpsEvent(
                type = ActivityType.perpsPeriodPosition_closed,
                meta = PerpsMetaEntity(
                    symbol = "BTC",
                    side = PerpsSide.Short,
                    reason = PerpsCloseReason.StopLoss,
                    marketIndex = 1,
                    leverage = 40,
                    entryPrice = BigDecimal("66541.70"),
                    closePrice = BigDecimal("67210.10"),
                    size = BigDecimal("0.00781"),
                    sizeUsd = BigDecimal("540.00"),
                    resultPct = BigDecimal("-1.01"),
                    fundingUsd = BigDecimal("-0.82"),
                ),
                outAmount = "1250000",
            ),
            market = null,
            onDismiss = {},
        )
    }
}