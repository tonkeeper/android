package com.tonapps.deposit.multicoin.screens.confirm.base.operation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.components.InsufficientFundsAction
import com.tonapps.core.components.InsufficientFundsDialog
import com.tonapps.deposit.multicoin.screens.confirm.formatAmountWithBalance
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.localization.Localization

/** What the balance falls short of when that is what stops a send, and what the user can do about it. */
sealed interface OperationShortfall {

    /**
     * Short of [account]'s own asset: more of it is bought. [required] is what is to be paid of it against
     * its balance, the fee alone when [forFees]; null when that is not known.
     */
    class Balance(
        val account: AccountWithDetails,
        val required: BigInteger? = null,
        val forFees: Boolean = false,
    ) : OperationShortfall

    /**
     * Short of [coin] for the fee alone, [fee] of it on [network], which a recharged battery would pay
     * instead. [transfer] when what is sent is a transfer.
     */
    class Fee(
        val coin: AccountWithDetails,
        val network: String,
        val fee: BigInteger?,
        val transfer: Boolean,
    ) : OperationShortfall
}

private val BatteryIllustration = UIKitIcon.ic_empty_battery_accent_flash_128

@Composable
internal fun OperationShortfallDialog(
    shortfall: OperationShortfall,
    walletId: String?,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onClose: () -> Unit,
) {
    when (shortfall) {
        is OperationShortfall.Balance -> BalanceShortfallDialog(shortfall, onTopUp, onClose)
        is OperationShortfall.Fee -> FeeShortfallDialog(shortfall, walletId, onTopUp, onOpenBattery, onClose)
    }
}

@Composable
private fun BalanceShortfallDialog(
    shortfall: OperationShortfall.Balance,
    onTopUp: (assetId: String) -> Unit,
    onClose: () -> Unit,
) {
    val asset = shortfall.account.asset
    val required = shortfall.required
    val message = if (required == null) {
        stringResource(Localization.insufficient_balance)
    } else {
        val (requiredText, balanceText) = formatAmountWithBalance(required, shortfall.account)
        val text = when {
            shortfall.forFees -> Localization.insufficient_balance_fees
            else -> Localization.insufficient_balance_default
        }
        stringResource(text, requiredText, balanceText)
    }
    InsufficientFundsDialog(
        title = stringResource(Localization.insufficient_balance_title),
        messages = listOf(message),
        iconRes = BatteryIllustration,
        tintIcon = false,
        actions = listOf(
            InsufficientFundsAction(
                text = stringResource(Localization.buy_ton, asset.symbol),
                primary = true,
                onClick = { onTopUp(asset.id) },
            ),
        ),
        onClose = onClose,
    )
}

@Composable
private fun FeeShortfallDialog(
    shortfall: OperationShortfall.Fee,
    walletId: String?,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onClose: () -> Unit,
) {
    val coin = shortfall.coin
    val symbol = coin.asset.symbol
    val fee = remember(shortfall.fee, coin) {
        shortfall.fee?.let { "${Formatter.formatShort(value = coin.asset.value.decimals.toDisplayUnit(it))} $symbol" } ?: symbol
    }
    val description = when {
        shortfall.transfer -> Localization.confirmation_insufficient_balance_transfer_description
        else -> Localization.confirmation_insufficient_balance_transaction_description
    }
    InsufficientFundsDialog(
        title = stringResource(Localization.confirmation_insufficient_balance, symbol),
        messages = listOf(stringResource(description, shortfall.network, fee, symbol)),
        iconRes = BatteryIllustration,
        tintIcon = false,
        actions = listOfNotNull(
            walletId?.let { id ->
                InsufficientFundsAction(
                    text = stringResource(Localization.recharge_battery),
                    primary = true,
                    onClick = { onOpenBattery(id, BatteryNativeFrom.InsufficientFunds) },
                )
            },
            InsufficientFundsAction(
                text = stringResource(Localization.deposit_asset, symbol),
                onClick = { onTopUp(coin.asset.id) },
            ),
        ),
        onClose = onClose,
    )
}
