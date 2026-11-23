package com.tonapps.deposit.multicoin.screens.confirm.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerSheet
import com.tonapps.deposit.multicoin.screens.confirm.OnUncovered
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.NetworkFeeCell
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmContent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationProperty
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.formattedValue
import com.tonapps.deposit.multicoin.screens.confirm.quotedValue
import com.tonapps.deposit.multicoin.screens.confirm.shortTitle
import com.tonapps.deposit.multicoin.screens.confirm.toPickerOption
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.TextCell
import ui.shortAddress
import ui.theme.UIKit
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.deposit.multicoin.screens.confirm.FeeCell
import com.tonapps.deposit.multicoin.screens.confirm.FeeQuotingCell
import com.tonapps.deposit.multicoin.screens.confirm.FeeSelectorCell
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import ui.components.moon.MoonItemDivider
import com.tonapps.deposit.multicoin.screens.confirm.networkStandardLabel

@Composable
fun TransferConfirmScreen(
    feature: TransferConfirmFeature,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    onSendSuccess: () -> Unit,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onConfirm: () -> Unit = {},
) {
    val account by feature.account.collectAsState()
    val asked by feature.asked.collectAsState()
    val amount by feature.amount.collectAsState()
    val feeMethods by feature.feeMethods.collectAsState()
    val feeMethod by feature.feeMethod.collectAsState()
    val sendingState by feature.sendingState.collectAsState()
    val feeQuoting by feature.feeQuoting.collectAsState()
    val wallet by feature.wallet.collectAsState()
    val clipboardManager = rememberClipboardManager()
    var showFeeMethods by remember { mutableStateOf(false) }
    val methodsSwitchable = feeMethods?.isPickerAvailable == true && sendingState == SendingState.None

    // Coming back from the battery refill must price the fee again.
    OnUncovered { feature.sendAction(OperationConfirmAction.RefreshFees) }

    OperationConfirmContent(
        feature = feature,
        title = stringResource(Localization.jetton_transfer, account?.asset?.symbol.orEmpty()),
        properties = transferProperties(feature.transfer.domain, account, asked, amount, onCopy = clipboardManager::copy),
        feeRow = { fee, feeRate ->
            TransferFeeCell(
                fee = fee,
                feeRate = feeRate,
                methods = feeMethods,
                method = feeMethod,
                quoting = feeQuoting,
                sending = sendingState != SendingState.None,
                onSwitchMethod = { showFeeMethods = true },
            )
        },
        confirmPrompt = if (feature.transfer.isMax == true) {
            stringResource(Localization.send_all_balance)
        } else {
            null
        },
        onBack = onBack,
        onClose = onClose,
        onSendSuccess = onSendSuccess,
        onTopUp = onTopUp,
        onOpenBattery = onOpenBattery,
        onConfirm = onConfirm,
    ) {
        val comment = asked?.memo
        if (!comment.isNullOrBlank()) {
            MoonItemDivider()
            CommentCell(
                comment = comment,
                encrypted = feature.transfer.commentEncryptionKey != null,
                onClick = { clipboardManager.copy(comment) },
            )
        }
    }

    val methods = feeMethods
    if (showFeeMethods && methods != null && methodsSwitchable) {
        FeeMethodPickerSheet(
            options = methods.pickerOptions.map { it.toPickerOption() },
            selectedId = feeMethod?.id,
            onSelect = { id -> methods.findOption(id)?.let(feature::pickFee) },
            onActionSelect = { id ->
                val walletId = wallet?.id
                if (walletId != null && methods.findOption(id)?.isBattery == true) {
                    onOpenBattery(walletId, BatteryNativeFrom.InsufficientFunds)
                }
            },
            onClose = { showFeeMethods = false },
        )
    }
}

@Composable
private fun transferProperties(
    domain: String?,
    account: AccountWithDetails?,
    asked: Transaction.Transfer?,
    amount: BigInteger?,
    onCopy: (String) -> Unit,
): List<OperationProperty> {
    account ?: return emptyList()
    val asset = account.asset.value
    return listOfNotNull(
        domain?.takeIf { it.isNotBlank() }?.let {
            OperationProperty(
                title = stringResource(Localization.recipient),
                value = it,
                onClick = { onCopy(it) },
                label = true,
                valueMaxLines = 2,
            )
        },
        asked?.let {
            val address = it.to.display
            OperationProperty(
                title = stringResource(Localization.recipient_address),
                value = address.shortAddress,
                onClick = { onCopy(address) },
                label = true,
                valueMaxLines = 2,
            )
        },
        OperationProperty(
            title = stringResource(Localization.network),
            value = asset.coin.name,
            description = asset.chain.networkStandardLabel,
        ),
        amount?.let {
            val display = asset.toDisplayUnit(it)
            val formatted = Formatter.formatShort(value = display, asset = asset)
            OperationProperty(
                title = stringResource(Localization.amount),
                value = formatted,
                description = account.rate?.let { rate -> Formatter.formatFiat(value = display, rate = rate.value, separator = " ") },
                onClick = { onCopy(formatted) },
            )
        },
    )
}

// The fee as the transaction confirm shows it: the picked method's, whose name opens the picker when
// there is another to pick, greyed out once the send is under way.
@Composable
private fun TransferFeeCell(
    fee: Fee,
    feeRate: FiatRate?,
    methods: TxFeeState?,
    method: TxFee?,
    quoting: Boolean,
    sending: Boolean,
    onSwitchMethod: () -> Unit,
) {
    if (methods == null) {
        NetworkFeeCell(fee, feeRate)
        return
    }

    method ?: return
    when {
        quoting -> FeeQuotingCell(divider = false)
        methods.isPickerAvailable -> FeeSelectorCell(
            value = method.quotedValue(),
            symbol = method.shortTitle(),
            enabled = !sending,
            onClick = onSwitchMethod,
            subtitle = null,
            divider = false,
        )
        else -> FeeCell(fee = method.formattedValue(), fiatFirst = false, subtitle = null, divider = false)
    }
}

@Composable
private fun CommentCell(comment: String, encrypted: Boolean, onClick: () -> Unit) {
    TextCell(
        modifier = Modifier.padding(vertical = 8.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MoonItemTitle(
                    text = stringResource(Localization.comment),
                    color = UIKit.colorScheme.text.secondary,
                )
                if (encrypted) {
                    MoonItemIcon(painterResource(UIKitIcon.ic_lock_16), color = UIKit.colorScheme.accent.green)
                }
            }
        },
        subtitle = {
            MoonItemTitle(text = comment, maxLines = 10)
        },
        minHeight = 82.dp,
        onClick = onClick,
    )
}
