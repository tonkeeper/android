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
)

data class PerpsAutoCloseIssue(
    @StringRes val text: Int,
    val liquidationPrice: BigDecimal? = null,
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
    val tp = takeProfit?.takeIf { it.signum() > 0 }
    val sl = stopLoss?.takeIf { it.signum() > 0 }

    return if (side == PerpsOrderSide.LONG) {
        PerpsAutoCloseCheck(
            takeProfit = PerpsAutoCloseIssue(Localization.perps_review_tp_update_text_long)
                .takeIf { tp != null && tp <= current },
            stopLoss = when {
                sl == null -> null
                sl >= current -> PerpsAutoCloseIssue(Localization.perps_auto_close_sl_below_current)
                liquidation != null && sl <= liquidation ->
                    PerpsAutoCloseIssue(Localization.perps_auto_close_sl_above_liquidation, liquidation)
                else -> null
            },
        )
    } else {
        PerpsAutoCloseCheck(
            takeProfit = PerpsAutoCloseIssue(Localization.perps_review_tp_update_text_short)
                .takeIf { tp != null && tp >= current },
            stopLoss = when {
                sl == null -> null
                sl <= current -> PerpsAutoCloseIssue(Localization.perps_auto_close_sl_above_current)
                liquidation != null && sl >= liquidation ->
                    PerpsAutoCloseIssue(Localization.perps_auto_close_sl_below_liquidation, liquidation)
                else -> null
            },
        )
    }
}

@Composable
fun perpsAutoCloseIssueText(issue: PerpsAutoCloseIssue, priceDecimals: Int): String {
    val liquidation = issue.liquidationPrice ?: return stringResource(issue.text)
    return stringResource(issue.text, formatUsdPrice(liquidation.toDouble(), priceDecimals))
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
