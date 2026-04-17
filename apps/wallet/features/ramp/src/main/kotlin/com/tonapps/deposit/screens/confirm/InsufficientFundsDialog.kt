package com.tonapps.deposit.screens.confirm

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.errors.InsufficientBalanceType
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.theme.UIKit

@Composable
fun InsufficientFundsDialog(
    event: ConfirmEvent.ShowInsufficientBalance,
    onBuyTon: () -> Unit,
    onGetTrx: () -> Unit,
    onRechargeBattery: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )
        InsufficientFundsContent(
            event = event,
            onBuyTon = {
                navigator.close()
                onBuyTon()
            },
            onGetTrx = {
                navigator.close()
                onGetTrx()
            },
            onRechargeBattery = {
                navigator.close()
                onRechargeBattery()
            },
        )
    }
}

@Composable
private fun InsufficientFundsContent(
    event: ConfirmEvent.ShowInsufficientBalance,
    onBuyTon: () -> Unit,
    onGetTrx: () -> Unit,
    onRechargeBattery: () -> Unit,
) {
    val isBattery = event.type == InsufficientBalanceType.InsufficientBatteryChargesForFee
    val isTrxFee = event.type == InsufficientBalanceType.InsufficientBalanceForFee

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        if (isBattery) {
            // Battery icon placeholder — matches legacy BatteryView at MIN_LEVEL
            Image(
                painter = painterResource(UIKitIcon.ic_flash_24),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
                colorFilter = ColorFilter.tint(UIKit.colorScheme.icon.secondary),
            )
        } else {
            Image(
                painter = painterResource(UIKitIcon.ic_exclamationmark_circle_84),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
                colorFilter = ColorFilter.tint(UIKit.colorScheme.icon.secondary),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Title
        Text(
            text = when {
                isBattery -> stringResource(Localization.insufficient_battery_charges)
                isTrxFee -> stringResource(Localization.insufficient_trx_balance)
                !event.singleWallet -> stringResource(Localization.insufficient_balance_in_wallet)
                else -> stringResource(Localization.insufficient_balance_title)
            },
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        // Description
        Text(
            text = formatDescription(event),
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // Recharge Battery button
        if (event.withRechargeBattery) {
            MoonButtonCell(
                text = stringResource(Localization.recharge_battery),
                onClick = onRechargeBattery,
            )
        }

        // Buy TON / Get TRX button
        if (!isBattery) {
            val buttonText = if (event.required.isTrx) {
                stringResource(Localization.get_token, TokenEntity.TRX.symbol)
            } else {
                stringResource(Localization.buy_ton).replace("TON", event.required.symbol)
            }
            MoonButtonCell(
                text = buttonText,
                colors = if (event.withRechargeBattery) {
                    MoonButtonCellDefaults.ButtonColorsSecondary
                } else {
                    MoonButtonCellDefaults.ButtonColorsPrimary
                },
                onClick = if (event.required.isTrx) onGetTrx else onBuyTon,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun formatDescription(event: ConfirmEvent.ShowInsufficientBalance): String {
    val balance = event.balance
    val required = event.required

    return if (event.type == InsufficientBalanceType.InsufficientBatteryChargesForFee) {
        stringResource(
            Localization.insufficient_balance_charges,
            CurrencyFormatter.format(value = required.value),
            CurrencyFormatter.format(value = balance.value),
        )
    } else {
        val balanceFormatted = CurrencyFormatter.formatFull(balance.symbol, balance.value, balance.decimals)
        val requiredFormatted = CurrencyFormatter.formatFull(required.symbol, required.value, required.decimals)
        val resId = if (event.withRechargeBattery || event.type == InsufficientBalanceType.InsufficientBalanceForFee) {
            Localization.insufficient_balance_fees
        } else {
            Localization.insufficient_balance_default
        }
        stringResource(resId, requiredFormatted, balanceFormatted)
    }
}
