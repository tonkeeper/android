package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.components.InsufficientFundsAction
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.core.components.InsufficientFundsDialog as SharedInsufficientFundsDialog
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.localization.Localization

private val BatteryIllustration = UIKitIcon.ic_empty_battery_accent_flash_128

@Composable
internal fun PendingTransaction.InsufficientFundsDialog(
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onClose: () -> Unit,
) {
    if (isShortOnFeeOnly()) {
        FeeShortageDialog(onTopUp, onOpenBattery, onClose)
    } else {
        BalanceShortageDialog(onTopUp, onClose)
    }
}

@Composable
private fun PendingTransaction.BalanceShortageDialog(
    onTopUp: (assetId: String) -> Unit,
    onClose: () -> Unit,
) {
    val shortfall = insufficientFunds()
    SharedInsufficientFundsDialog(
        title = stringResource(Localization.insufficient_balance_title),
        messages = listOf(shortfall.description),
        iconRes = BatteryIllustration,
        tintIcon = false,
        actions = listOf(
            InsufficientFundsAction(
                text = stringResource(Localization.buy_ton, shortfall.buySymbol),
                primary = true,
                onClick = { onTopUp(shortfall.buyAssetId) },
            ),
        ),
        onClose = onClose,
    )
}

@Composable
private fun PendingTransaction.FeeShortageDialog(
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onClose: () -> Unit,
) {
    val shortfall = feeShortfall()
    SharedInsufficientFundsDialog(
        title = stringResource(Localization.confirmation_insufficient_balance, shortfall.symbol),
        messages = listOf(
            stringResource(
                Localization.confirmation_insufficient_balance_description,
                shortfall.network,
                shortfall.fee,
                shortfall.symbol,
            ),
        ),
        iconRes = BatteryIllustration,
        tintIcon = false,
        actions = listOf(
            InsufficientFundsAction(
                text = stringResource(Localization.recharge_battery),
                primary = true,
                onClick = {
                    onOpenBattery(wallet.id, BatteryNativeFrom.InsufficientFunds)
                },
            ),
            InsufficientFundsAction(
                text = stringResource(Localization.deposit_asset, shortfall.symbol),
                onClick = { onTopUp(shortfall.assetId) },
            ),
        ),
        onClose = onClose,
    )
}

private data class InsufficientFunds(
    val buyAssetId: String,
    val buySymbol: String,
    val description: String,
)

@Composable
private fun PendingTransaction.insufficientFunds(): InsufficientFunds {
    val amount = spentAmount()

    return when (account.asset.value) {
        is Asset.Token -> tokenShortfall(amount)
        is Asset.Coin, is Asset.Battery -> coinShortfall(amount)
    }
}

// A token send is short either on the token itself or on the native coin paying the fees; the
// native-coin account is [energy], so the two cases buy different assets.
@Composable
private fun PendingTransaction.tokenShortfall(amount: BigInteger?): InsufficientFunds {
    if (amount != null && amount > account.unitBalance.value) {
        val (amountFormat, balanceFormat) = formatAmountWithBalance(amount, account)
        return InsufficientFunds(
            buyAssetId = account.asset.id,
            buySymbol = account.asset.symbol,
            description = stringResource(
                Localization.insufficient_balance_default,
                amountFormat,
                balanceFormat,
            ),
        )
    }

    val energy = fee.energy ?: return genericShortfall()
    val estimated = fee.estimated ?: return genericShortfall()

    // The gas reserve that raised the error checks the main fee only, so the approval fee
    // stays out of the "required" amount.
    val required = TxFeeLogic.requiredEnergy(estimated, fee.attachedEnergy)
    val (feeFormat, balanceFormat) = formatAmountWithBalance(required, energy)
    return InsufficientFunds(
        buyAssetId = energy.asset.id,
        buySymbol = energy.asset.symbol,
        description = stringResource(
            Localization.insufficient_balance_fees,
            feeFormat,
            balanceFormat,
        ),
    )
}

@Composable
private fun PendingTransaction.coinShortfall(amount: BigInteger?): InsufficientFunds {
    amount ?: return genericShortfall()

    // With no fee estimate and the amount itself covered, "To be paid: amount / Your balance"
    // would read as if nothing is wrong — the shortfall is in the unknown fee.
    if (fee.estimated == null && amount <= account.unitBalance.value) {
        return genericShortfall()
    }

    val required = amount + (fee.estimated?.amount ?: BigInteger.ZERO)
    val (requiredFormat, balanceFormat) = formatAmountWithBalance(required, account)
    return InsufficientFunds(
        buyAssetId = account.asset.id,
        buySymbol = account.asset.symbol,
        description = stringResource(
            Localization.insufficient_balance_default,
            requiredFormat,
            balanceFormat,
        ),
    )
}

@Composable
private fun PendingTransaction.genericShortfall(): InsufficientFunds {
    val asset = (fee.energy ?: account).asset
    return InsufficientFunds(
        buyAssetId = asset.id,
        buySymbol = asset.symbol,
        description = stringResource(Localization.insufficient_balance),
    )
}

@Composable
private fun formatAmountWithBalance(
    amount: BigInteger,
    account: AccountWithDetails,
): Pair<String, String> {
    return remember(amount, account) {
        val symbol = account.asset.symbol
        val amountDisplay = account.asset.value.decimals.toDisplayUnit(amount)
        val amountShort = "${Formatter.formatShort(value = amountDisplay)} $symbol"
        val balanceShort = "${Formatter.formatShort(value = account.displayBalance)} $symbol"
        if (amount == account.unitBalance.value || amountShort != balanceShort) {
            amountShort to balanceShort
        } else {
            "${Formatter.formatAsset(value = amountDisplay)} $symbol" to
                "${Formatter.formatAsset(value = account.displayBalance)} $symbol"
        }
    }
}

private fun PendingTransaction.spentAmount(): BigInteger? = spentAmount ?: when (signing) {
    is Signing.Msg -> null
    is Signing.Tx -> signing.value.amount
}

/**
 * Whether the battery would cover the shortage. It pays the fee, never what is sent: a coin account
 * that cannot cover its own amount is short on balance whatever pays the fee.
 */
internal fun PendingTransaction.isShortOnFeeOnly(): Boolean {
    if (!batteryRechargeHelps()) {
        return false
    }
    val amount = spentAmount() ?: return true
    return account.asset.value !is Asset.Coin || amount <= account.unitBalance.value
}

// Only a battery the relayer priced for this transaction, and whose charges fall short, is worth
// recharging: a failed estimate, enough charges or an operation the battery does not pay leave the
// chain's coin as the only way out.
private fun PendingTransaction.batteryRechargeHelps(): Boolean {
    if (fee.estimated == null) {
        return false
    }
    return fee.batteryRechargeable
}

private data class FeeShortfall(
    val assetId: String,
    val symbol: String,
    val network: String,
    val fee: String,
)

@Composable
private fun PendingTransaction.feeShortfall(): FeeShortfall {
    val energy = fee.energy ?: account
    val requiredEnergy = fee.requiredEnergy
    return remember(energy, requiredEnergy, account) {
        val symbol = energy.asset.symbol
        val amount = requiredEnergy?.let { energy.asset.value.decimals.toDisplayUnit(it) }
        FeeShortfall(
            assetId = energy.asset.id,
            symbol = symbol,
            network = account.asset.value.chain.name,
            fee = amount?.let { "${Formatter.formatAsset(value = it)} $symbol" } ?: symbol,
        )
    }
}
