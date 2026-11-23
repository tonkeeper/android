package com.tonapps.swap.screens.swap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeType
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeWalletMode
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeeSummary
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf
import ui.utils.uppercased

private val Presets = listOf(25, 50, 75, 100)

internal fun SwapFeature.onSwapClick() {
    val sell = sellAsset.value
    val buy = buyAsset.value
    if (!sendsInPlace && sell != null && buy != null) {
        AnalyticsHelper.Default.eventsTaggedWith(utm).swapsNative.swapClick(
            type = SwapsNativeType.Native,
            walletMode = SwapsNativeWalletMode.Multi,
            assetFrom = sell.id,
            assetTo = buy.id,
            isMax = amountInput.value.isMax,
        )
    }
    sendAction(SwapAction.Continue)
}

@Composable
internal fun RowScope.SwapFeeLineRow(
    line: SwapFeeLine,
    onDetails: () -> Unit,
    onPromo: () -> Unit,
    onPromoShown: () -> Unit,
) {
    when (line) {
        SwapFeeLine.Hidden -> Unit

        SwapFeeLine.Fetching,
        SwapFeeLine.Estimating -> {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = UIKit.colorScheme.icon.primary,
            )
            MoonItemSubtitle(
                text = when (line) {
                    SwapFeeLine.Fetching -> stringResource(Localization.swap_fetching_quote)
                    else -> stringResource(Localization.swap_fee_estimating)
                },
            )
        }

        SwapFeeLine.BareNetworkFee -> MoonItemSubtitle(text = stringResource(Localization.network_fee))

        SwapFeeLine.Promo -> {
            LaunchedEffect(Unit) { onPromoShown() }
            FeeLineButton(
                text = stringResource(Localization.swap_fee_battery_promo),
                iconRes = UIKitIcon.ic_battery_28,
                color = UIKit.colorScheme.accent.blue,
                chevronColor = UIKit.colorScheme.accent.blue,
                onClick = onPromo,
            )
        }

        is SwapFeeLine.QuoteFailed -> QuoteAlert(
            text = stringResource(line.messageRes),
            color = UIKit.colorScheme.accent.red,
            onClick = null,
        )

        is SwapFeeLine.Fee -> {
            val summary = line.summary
            val shortage = summary.isUnpaid || summary == SwapFeeSummary.Unknown
            FeeLineButton(
                text = summary.lineText(),
                iconRes = when {
                    shortage -> UIKitIcon.ic_exclamationmark_circle_16
                    summary is SwapFeeSummary.Charges -> UIKitIcon.ic_battery_28
                    else -> null
                },
                color = when {
                    shortage -> UIKit.colorScheme.accent.red
                    else -> UIKit.colorScheme.text.secondary
                },
                chevronColor = UIKit.colorScheme.icon.tertiary,
                onClick = onDetails.takeIf { line.tappable },
            )
        }
    }
}

@Composable
private fun SwapFeeSummary.lineText(): String = when (this) {
    is SwapFeeSummary.Charges -> stringResource(
        Localization.swap_fee_charges,
        pluralStringResource(Plurals.battery_charges, count, String.format(CurrencyFormatter.locale, "%d", count)),
    )
    is SwapFeeSummary.Amount -> stringResource(Localization.swap_fee_amount, text)
    SwapFeeSummary.BatteryEmpty -> stringResource(Localization.swap_fee_battery_empty)
    SwapFeeSummary.LowBattery -> stringResource(Localization.swap_fee_low_battery)
    is SwapFeeSummary.NoFunds -> stringResource(Localization.swap_fee_no_funds, symbol)
    SwapFeeSummary.Unknown -> stringResource(Localization.swap_fee_failed)
}

@Composable
private fun FeeLineButton(
    text: String,
    iconRes: Int?,
    color: Color,
    chevronColor: Color,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .modifyIf { onClick?.let { clickable(onClick = it) } },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            MoonItemIcon(
                painter = painterResource(iconRes),
                size = 16.dp,
                color = color,
            )
        }
        MoonItemSubtitle(
            modifier = Modifier.weight(1f, fill = false),
            text = text,
            maxLines = 1,
            color = color,
        )
        if (onClick != null) {
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_chevron_right_16),
                size = 16.dp,
                color = chevronColor,
            )
        }
    }
}

@Composable
private fun QuoteAlert(
    text: String,
    color: Color,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .modifyIf { onClick?.let { clickable(onClick = it) } },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_exclamationmark_circle_16),
            modifier = Modifier.padding(top = 2.dp),
            size = 16.dp,
            color = color,
        )
        MoonItemSubtitle(
            text = text,
            maxLines = Int.MAX_VALUE,
            color = color,
        )
    }
}

@Composable
internal fun SwapBottomBar(
    notice: String?,
    button: SwapButton,
    presetsEnabled: Boolean,
    onPreset: (Int) -> Unit,
    onSwap: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.modifyIf { alpha(0f).clearAndSetSemantics {}.takeIf { notice != null } }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Presets.forEach { percent ->
                    MoonAccentButton(
                        modifier = Modifier.weight(1f),
                        text = when (percent) {
                            100 -> stringResource(Localization.max).uppercased()
                            else -> CurrencyFormatter.formatPercent(percent).toString()
                        },
                        size = ButtonSizeSmall,
                        buttonColors = ButtonColorsSecondary,
                        enabled = presetsEnabled && notice == null,
                        onClick = { onPreset(percent) },
                    )
                }
            }
            SwapActionButton(
                button = button,
                colors = MoonButtonCellDefaults.ButtonColorsPrimary,
                onClick = onSwap.takeIf { notice == null } ?: {},
            )
        }
        if (notice != null) {
            SwapNoticeBar(notice)
        }
    }
}

@Composable
internal fun SwapActionButton(
    button: SwapButton,
    colors: ButtonColors,
    onClick: () -> Unit,
) {
    MoonBottomButtonCell(
        text = when (button) {
            SwapButton.Sending,
            SwapButton.FeeUnpaid,
            is SwapButton.Swap -> stringResource(Localization.swap)

            SwapButton.Done -> stringResource(Localization.done)
            SwapButton.EnterAmount -> stringResource(Localization.enter_amount)
            is SwapButton.Minimum -> stringResource(Localization.minimum_amount, button.amount)
            SwapButton.InsufficientBalance -> stringResource(Localization.insufficient_balance)
            SwapButton.StatusUnknown -> stringResource(Localization.swap_status_unknown)
        },
        enabled = button.isTappable,
        loading = button == SwapButton.Sending,
        colors = when (button) {
            SwapButton.Sending,
            SwapButton.Done,
            is SwapButton.Swap -> colors

            else -> MoonButtonCellDefaults.ButtonColorsSecondary
        },
        onClick = onClick,
    )
}

@Composable
internal fun SwapNoticeBar(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_exclamationmark_circle_28),
            size = 28.dp,
            color = UIKit.colorScheme.accent.red,
        )
        Text(
            text = text,
            color = UIKit.colorScheme.accent.red,
            style = UIKit.typography.label1,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun SwapFeeLinePreview() {
    ThemedPreview {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                SwapFeeLine.Estimating,
                SwapFeeLine.Fee(SwapFeeSummary.Charges(7), tappable = true),
                SwapFeeLine.Fee(SwapFeeSummary.Amount("0.03 GRAM"), tappable = true),
                SwapFeeLine.Fee(SwapFeeSummary.Amount("0.00562 GRAM"), tappable = false),
                SwapFeeLine.Fee(SwapFeeSummary.BatteryEmpty, tappable = true),
                SwapFeeLine.Fee(SwapFeeSummary.LowBattery, tappable = true),
                SwapFeeLine.Fee(SwapFeeSummary.NoFunds("GRAM"), tappable = true),
                SwapFeeLine.Fee(SwapFeeSummary.Unknown, tappable = false),
                SwapFeeLine.BareNetworkFee,
                SwapFeeLine.Promo,
            ).forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwapFeeLineRow(line = line, onDetails = {}, onPromo = {}, onPromoShown = {})
                }
            }
        }
    }
}

@Preview
@Composable
private fun SwapActionButtonPreview() {
    ThemedPreview {
        Column {
            listOf(
                SwapButton.Swap(enabled = true),
                SwapButton.Swap(enabled = false),
                SwapButton.FeeUnpaid,
                SwapButton.InsufficientBalance,
                SwapButton.Minimum("1 GRAM"),
                SwapButton.EnterAmount,
            ).forEach { button ->
                SwapActionButton(button = button, colors = MoonButtonCellDefaults.ButtonColorsPrimary, onClick = {})
            }
        }
    }
}
