package com.tonapps.perps.screens.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.wallet.localization.Localization
import ui.components.moon.dialog.MoonAlertDialog
import ui.preview.ThemedPreview

@Composable
fun PerpsLimitEditReviewDialog(
    symbol: String,
    side: PerpsPositionSide,
    sizeText: String,
    oldPriceText: String,
    newPriceText: String,
    removesTakeProfit: Boolean,
    removesStopLoss: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sideText = stringResource(
        when (side) {
            PerpsPositionSide.LONG -> Localization.perps_long
            PerpsPositionSide.SHORT -> Localization.perps_short
        }
    )
    val priceText = stringResource(Localization.perps_size_change_arrow_pattern, oldPriceText, newPriceText)
    val lines = listOfNotNull(
        stringResource(Localization.perps_order_amount_pattern, stringResource(Localization.perps_size), sizeText),
        stringResource(Localization.perps_order_price, priceText),
        stringResource(Localization.perps_limit_price_take_profit_removed).takeIf { removesTakeProfit },
        stringResource(Localization.perps_limit_price_stop_loss_removed).takeIf { removesStopLoss },
    )

    MoonAlertDialog(
        title = stringResource(Localization.perps_review_title_pattern, symbol, sideText),
        message = lines.joinToString("\n"),
        positiveButtonText = stringResource(Localization.confirm),
        onDismiss = onDismiss,
        onPositiveClick = onConfirm,
        negativeButtonText = stringResource(Localization.cancel),
    )
}

@Preview
@Composable
private fun PerpsLimitEditReviewLongPreview() {
    ThemedPreview {
        PerpsLimitEditReviewDialog(
            symbol = "ETH",
            side = PerpsPositionSide.LONG,
            sizeText = "0.012 ETH",
            oldPriceText = "$1,800.00",
            newPriceText = "$2,118.20",
            removesTakeProfit = false,
            removesStopLoss = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsLimitEditReviewShortLegsPreview() {
    ThemedPreview {
        PerpsLimitEditReviewDialog(
            symbol = "BTC",
            side = PerpsPositionSide.SHORT,
            sizeText = "0.0015 BTC",
            oldPriceText = "$98,250.00",
            newPriceText = "$101,400.00",
            removesTakeProfit = true,
            removesStopLoss = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
