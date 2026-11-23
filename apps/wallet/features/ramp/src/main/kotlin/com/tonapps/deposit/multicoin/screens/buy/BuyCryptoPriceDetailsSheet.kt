package com.tonapps.deposit.multicoin.screens.buy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentFee
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentFeeKind
import com.tonapps.deposit.multicoin.screens.moonpay.title
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonDivider
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit

// Follows the quote on screen while open: a refreshed quote updates its rows, and the sheet closes once the
// screen has no quote to break down, still showing the rows it last had.
@Composable
internal fun BuyCryptoPriceDetailsSheet(
    details: BuyCryptoPriceDetails?,
    timer: Float?,
    onToggleRate: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose)
    var last by remember { mutableStateOf(details) }
    LaunchedEffect(details) {
        if (details != null) {
            last = details
        } else if (last == null) {
            onClose()
        } else {
            navigator.close()
        }
    }
    val shown = details ?: last ?: return
    MoonModalDialog(navigator = navigator) {
        PriceDetailsContent(details = shown, timer = timer, onToggleRate = onToggleRate, onClose = { navigator.close() })
    }
}

@Composable
private fun PriceDetailsContent(
    details: BuyCryptoPriceDetails,
    timer: Float?,
    onToggleRate: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonTopAppBarSimple(
            title = stringResource(Localization.buy_crypto_price_details),
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = onClose,
            backgroundColor = Color.Transparent,
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .clip(UIKit.shapes.large)
                .background(UIKit.colorScheme.background.content),
        ) {
            DetailsRow(title = stringResource(Localization.buy_crypto_price_details_you_pay)) {
                Value(details.youPay)
            }
            DetailsRow(title = stringResource(Localization.buy_crypto_price_details_rate)) {
                Row(
                    modifier = Modifier.clickable(onClick = onToggleRate),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (details.rate.refreshing) {
                        RateSpinner()
                    } else {
                        RateTimer(timer)
                    }
                    Text(
                        text = stringResource(Localization.buy_crypto_rate, details.rate.from, details.rate.to),
                        style = UIKit.typography.body1,
                        color = UIKit.colorScheme.text.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        painter = painterResource(UIKitIcon.ic_swap_horizontal_16),
                        contentDescription = null,
                        tint = UIKit.colorScheme.icon.tertiary,
                    )
                }
            }
            details.fees.forEach { fee ->
                DetailsRow(title = stringResource(fee.kind.title)) {
                    Value(fee.amount)
                }
            }
            DetailsRow(title = stringResource(Localization.buy_crypto_price_details_you_get), divider = false) {
                Value(details.youGet)
            }
        }
    }
}

@Composable
private fun DetailsRow(title: String, divider: Boolean = true, value: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.text.secondary,
                maxLines = 1,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                value()
            }
        }
        if (divider) {
            MoonDivider(modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp))
        }
    }
}

@Composable
private fun Value(text: String) {
    Text(
        text = text,
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.text.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
    )
}

private val ROW_HEIGHT = 56.dp

@Preview
@Composable
private fun PriceDetailsPreview() {
    ThemedPreview {
        PriceDetailsContent(
            details = BuyCryptoPriceDetails(
                youPay = "$200",
                rate = BuyCryptoRate.Quoted(from = "USD", to = "0.935 USDT (TON)", refreshing = false),
                fees = listOf(
                    MoonPayPaymentFee(MoonPayPaymentFeeKind.MoonPay, "$7.90"),
                    MoonPayPaymentFee(MoonPayPaymentFeeKind.Ecosystem, "$5.40"),
                ),
                youGet = "187.07 USDT (TON)",
            ),
            timer = 0.6f,
            onToggleRate = {},
            onClose = {},
        )
    }
}
