package com.tonapps.migration.screens.confirm

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tonapps.core.components.InsufficientFundsAction
import com.tonapps.core.components.InsufficientFundsDialog as SharedInsufficientFundsDialog
import com.tonapps.core.components.InsufficientFundsWallet
import com.tonapps.migration.data.MigrationFeeShortage
import com.tonapps.migration.data.formatBalance
import com.tonapps.migration.data.formatRequired
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals

@Composable
internal fun MigrationFeeShortage.InsufficientFundsDialog(
    onDeposit: () -> Unit,
    onClose: () -> Unit,
) {
    val continueNote = continueNote()

    SharedInsufficientFundsDialog(
        title = stringResource(titleRes),
        wallet = dialogWallet(),
        messages = listOf(
            stringResource(
                Localization.migration_insufficient_required,
                formatRequiredAmount(),
            ),
            stringResource(
                Localization.migration_insufficient_balance,
                formatBalanceAmount(),
            ),
        ),
        note = continueNote?.let {
            stringResource(
                Localization.migration_insufficient_partial_note,
                it.payChain,
                it.blockedChain,
                it.blockedAsset,
            )
        },
        actions = buildList {
            if (continueNote != null) {
                add(
                    InsufficientFundsAction(
                        text = stringResource(Localization.continue_action),
                        primary = true,
                        onClick = {},
                    ),
                )
            }
            add(
                InsufficientFundsAction(
                    text = stringResource(depositActionRes),
                    primary = continueNote == null,
                    onClick = onDeposit,
                ),
            )
        },
        onClose = onClose,
    )
}

private val MigrationFeeShortage.titleRes: Int
    @StringRes get() = when (this) {
        is MigrationFeeShortage.Ton -> Localization.migration_insufficient_ton_title
        is MigrationFeeShortage.Trx -> Localization.migration_insufficient_trx_title
        is MigrationFeeShortage.Both -> Localization.migration_insufficient_funds_title
        is MigrationFeeShortage.Battery -> Localization.insufficient_battery_charges
    }

private val MigrationFeeShortage.depositActionRes: Int
    @StringRes get() = when (this) {
        is MigrationFeeShortage.Ton -> Localization.migration_deposit_ton
        is MigrationFeeShortage.Trx -> Localization.migration_deposit_trx
        is MigrationFeeShortage.Both -> Localization.migration_deposit_wallet
        is MigrationFeeShortage.Battery -> Localization.recharge_battery
    }

private fun MigrationFeeShortage.dialogWallet(): InsufficientFundsWallet? {
    if (this is MigrationFeeShortage.Battery) {
        return null
    }
    return InsufficientFundsWallet(emoji = walletEmoji, name = walletName)
}

private data class ContinueNote(
    val payChain: String,
    val blockedChain: String,
    val blockedAsset: String,
)

private fun MigrationFeeShortage.continueNote(): ContinueNote? = when {
    this is MigrationFeeShortage.Ton && canContinueWithTron -> ContinueNote("TRON", "TON", "GRAM")
    this is MigrationFeeShortage.Trx && canContinueWithTon -> ContinueNote("TON", "TRON", "TRX")
    this is MigrationFeeShortage.Battery && canContinueWithTon -> ContinueNote("TON", "TRON", "Battery")
    this is MigrationFeeShortage.Battery && canContinueWithTron -> ContinueNote("TRON", "TON", "Battery")
    else -> null
}

@Composable
private fun MigrationFeeShortage.formatRequiredAmount(): String = when (this) {
    is MigrationFeeShortage.Battery -> pluralStringResource(
        Plurals.battery_charges,
        requiredCharges,
        requiredCharges,
    )
    else -> formatRequired()
}

@Composable
private fun MigrationFeeShortage.formatBalanceAmount(): String = when (this) {
    is MigrationFeeShortage.Battery -> pluralStringResource(
        Plurals.battery_charges,
        availableCharges,
        availableCharges,
    )
    else -> formatBalance()
}
