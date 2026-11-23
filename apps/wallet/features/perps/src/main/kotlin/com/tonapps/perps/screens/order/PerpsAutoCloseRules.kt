package com.tonapps.perps.screens.order

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.wallet.localization.Localization
import ui.theme.UIKit
import java.math.BigDecimal

data class PerpsAutoCloseBounds(
    val entryPrice: BigDecimal,
    val leverage: BigDecimal,
    val currentPrice: BigDecimal,
    val liquidationPrice: BigDecimal?,
    val closestTakeProfit: BigDecimal? = null,
    val closestStopLoss: BigDecimal? = null,
)

data class PerpsAutoCloseIssue(
    @StringRes val text: Int,
    val price: BigDecimal? = null,
)

data class PerpsAutoCloseCheck(
    val takeProfit: PerpsAutoCloseIssue?,
    val stopLoss: PerpsAutoCloseIssue?,
) {
    val isValid: Boolean
        get() = takeProfit == null && stopLoss == null
}

fun checkAutoClose(
    side: PerpsOrderSide,
    takeProfit: BigDecimal?,
    stopLoss: BigDecimal?,
    bounds: PerpsAutoCloseBounds,
): PerpsAutoCloseCheck {
    val current = bounds.currentPrice
    if (current.signum() <= 0) {
        return PerpsAutoCloseCheck(takeProfit = null, stopLoss = null)
    }
    val liquidation = bounds.liquidationPrice?.takeIf { it.signum() > 0 }
    val closestTakeProfit = bounds.closestTakeProfit?.takeIf { it.signum() > 0 }
    val closestStopLoss = bounds.closestStopLoss?.takeIf { it.signum() > 0 }
    val tp = takeProfit?.takeIf { it.signum() > 0 }
    val sl = stopLoss?.takeIf { it.signum() > 0 }

    return if (side == PerpsOrderSide.LONG) {
        PerpsAutoCloseCheck(
            takeProfit = when {
                tp == null -> null
                tp <= current -> PerpsAutoCloseIssue(Localization.perps_review_tp_update_text_long)
                closestTakeProfit != null && tp < closestTakeProfit ->
                    PerpsAutoCloseIssue(Localization.perps_auto_close_tp_at_least, closestTakeProfit)
                else -> null
            },
            stopLoss = when {
                sl == null -> null
                sl >= current -> PerpsAutoCloseIssue(Localization.perps_auto_close_sl_below_current)
                closestStopLoss != null && sl > closestStopLoss ->
                    PerpsAutoCloseIssue(Localization.perps_auto_close_sl_at_most, closestStopLoss)
                liquidation != null && sl <= liquidation ->
                    PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, liquidation)
                else -> null
            },
        )
    } else {
        PerpsAutoCloseCheck(
            takeProfit = when {
                tp == null -> null
                tp >= current -> PerpsAutoCloseIssue(Localization.perps_review_tp_update_text_short)
                closestTakeProfit != null && tp > closestTakeProfit ->
                    PerpsAutoCloseIssue(Localization.perps_auto_close_tp_at_most, closestTakeProfit)
                else -> null
            },
            stopLoss = when {
                sl == null -> null
                sl <= current -> PerpsAutoCloseIssue(Localization.perps_auto_close_sl_above_current)
                closestStopLoss != null && sl < closestStopLoss ->
                    PerpsAutoCloseIssue(Localization.perps_auto_close_sl_at_least, closestStopLoss)
                liquidation != null && sl >= liquidation ->
                    PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_below, liquidation)
                else -> null
            },
        )
    }
}

@Composable
fun perpsAutoCloseIssueText(issue: PerpsAutoCloseIssue, priceDecimals: Int): String {
    val price = issue.price ?: return stringResource(issue.text)
    return stringResource(issue.text, formatUsdPrice(price.toDouble(), priceDecimals))
}

@Composable
fun perpsAutoCloseSummary(takeProfit: String?, stopLoss: String?): AnnotatedString {
    val takeProfitText = takeProfit?.let { stringResource(Localization.perps_auto_close_tp_value, it) }
    val stopLossText = stopLoss?.let { stringResource(Localization.perps_auto_close_sl_value, it) }
    val separator = stringResource(Localization.perps_auto_close_legs_separator)
    val separatorColor = UIKit.colorScheme.text.tertiary

    return buildAnnotatedString {
        takeProfitText?.let(::append)
        if (takeProfitText != null && stopLossText != null) {
            withStyle(SpanStyle(color = separatorColor)) {
                append(separator)
            }
        }
        stopLossText?.let(::append)
    }
}
