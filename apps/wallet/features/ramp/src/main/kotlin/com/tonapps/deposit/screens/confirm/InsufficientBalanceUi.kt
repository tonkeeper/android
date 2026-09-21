package com.tonapps.deposit.screens.confirm

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.errors.InsufficientBalanceType
import com.tonapps.core.components.InsufficientFundsAction
import com.tonapps.core.components.InsufficientFundsDialog as SharedInsufficientFundsDialog
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization

@Composable
internal fun ConfirmEvent.ShowInsufficientBalance.InsufficientFundsDialog(
    onBuyTon: () -> Unit,
    onGetTrx: () -> Unit,
    onRechargeBattery: () -> Unit,
    onClose: () -> Unit,
) {
    val isBattery = type == InsufficientBalanceType.InsufficientBatteryChargesForFee
    val isTrxFee = type == InsufficientBalanceType.InsufficientBalanceForFee

    SharedInsufficientFundsDialog(
        iconRes = if (isBattery) UIKitIcon.ic_flash_24 else UIKitIcon.ic_exclamationmark_circle_84,
        title = when {
            isBattery -> stringResource(Localization.insufficient_battery_charges)
            isTrxFee -> stringResource(Localization.insufficient_trx_balance)
            !singleWallet -> stringResource(Localization.insufficient_balance_in_wallet)
            else -> stringResource(Localization.insufficient_balance_title)
        },
        messages = listOf(formatDescription()),
        actions = buildList {
            if (withRechargeBattery) {
                add(
                    InsufficientFundsAction(
                        text = stringResource(Localization.recharge_battery),
                        primary = true,
                        onClick = onRechargeBattery,
                    ),
                )
            }
            if (!isBattery) {
                add(
                    InsufficientFundsAction(
                        text = if (required.isTrx) {
                            stringResource(Localization.get_token, TokenEntity.TRX.symbol)
                        } else {
                            stringResource(Localization.buy_ton, required.symbol)
                        },
                        primary = !withRechargeBattery,
                        onClick = if (required.isTrx) onGetTrx else onBuyTon,
                    ),
                )
            }
        },
        onClose = onClose,
    )
}

@Composable
private fun ConfirmEvent.ShowInsufficientBalance.formatDescription(): String {
    if (type == InsufficientBalanceType.InsufficientBatteryChargesForFee) {
        return stringResource(
            Localization.insufficient_balance_charges,
            CurrencyFormatter.format(value = required.value),
            CurrencyFormatter.format(value = balance.value),
        )
    }

    val resId = if (withRechargeBattery || type == InsufficientBalanceType.InsufficientBalanceForFee) {
        Localization.insufficient_balance_fees
    } else {
        Localization.insufficient_balance_default
    }
    return stringResource(
        resId,
        CurrencyFormatter.formatFull(required.symbol, required.value, required.decimals),
        CurrencyFormatter.formatFull(balance.symbol, balance.value, balance.decimals),
    )
}
