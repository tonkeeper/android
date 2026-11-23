package com.tonapps.deposit.multicoin.screens.confirm.dapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.deposit.multicoin.screens.confirm.ExpandableJsonList
import com.tonapps.deposit.multicoin.screens.confirm.ExpandedIcon
import com.tonapps.deposit.multicoin.screens.confirm.OnUncovered
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmContent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationProperty
import com.tonapps.deposit.multicoin.screens.confirm.parseExpandableJson
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonExpandable
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.TextCell
import ui.shortAddress
import ui.theme.UIKit
import com.tonapps.blockchain.model.DappConnectRequest.DomainStatus
import com.tonapps.deposit.multicoin.screens.confirm.networkStandardLabel

@Composable
fun DappTxConfirmScreen(
    feature: DappTxConfirmFeature,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onConfirm: () -> Unit = {},
) {
    val account by feature.account.collectAsState()
    val transaction by feature.transaction.collectAsState()
    val clipboardManager = rememberClipboardManager()

    val title = when {
        feature.isCall -> stringResource(Localization.call_contract)
        else -> stringResource(Localization.jetton_transfer, account?.asset?.symbol.orEmpty()).trim()
    }
    val properties = buildList {
        transaction?.recipient()?.let { recipient ->
            add(
                OperationProperty(
                    title = stringResource(Localization.recipient_address),
                    value = recipient.shortAddress,
                    onClick = { clipboardManager.copy(recipient) },
                    label = true,
                    valueMaxLines = 2,
                ),
            )
        }
        val appName = feature.dapp.name
        if (appName.isNotBlank()) {
            add(
                OperationProperty(
                    title = "App",
                    value = appName,
                    onClick = { clipboardManager.copy(appName) },
                    label = true,
                    // A domain the wallet flags shows red, as the swipe's warning does.
                    valueColor = when (feature.dapp.domainStatus) {
                        DomainStatus.Invalid,
                        DomainStatus.Scam -> UIKit.colorScheme.accent.red
                        DomainStatus.Valid,
                        DomainStatus.Unknown,
                        null -> UIKit.colorScheme.accent.blue
                    },
                ),
            )
        }
        account?.let { position ->
            add(
                OperationProperty(
                    title = stringResource(Localization.network),
                    value = position.asset.value.coin.name,
                    description = position.asset.value.chain.networkStandardLabel,
                ),
            )
        }
        val amount = transaction?.shownAmount()
        val position = account
        if (amount != null && position != null) {
            add(amountProperty(position, amount, onCopy = { clipboardManager.copy(it) }))
        }
    }

    // Coming back from a top-up or the battery refill must price the transaction again.
    OnUncovered { feature.sendAction(OperationConfirmAction.RefreshFees) }

    OperationConfirmContent(
        feature = feature,
        title = title,
        properties = properties,
        domainRisk = feature.dapp.domainStatus,
        onBack = onBack,
        onClose = onClose,
        onSendSuccess = onSendSuccess,
        onTopUp = onTopUp,
        onOpenBattery = onOpenBattery,
        onConfirm = onConfirm,
    ) {
        val content = (transaction as? Transaction.Call)?.data
        if (!content.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            SigningContent(content)
        }
    }
}

@Composable
private fun amountProperty(
    position: AccountWithDetails,
    amount: BigInteger,
    onCopy: (String) -> Unit,
): OperationProperty {
    val asset = position.asset.value
    val display = asset.toDisplayUnit(amount)
    val formatted = remember(display) { Formatter.formatShort(value = display, asset = asset) }
    val fiat = remember(display, position.rate) {
        position.rate?.let { Formatter.formatFiat(value = display, rate = it.value, separator = " ") }
    }
    return OperationProperty(
        title = stringResource(Localization.amount),
        value = formatted,
        description = fiat,
        onClick = { onCopy(formatted) },
    )
}

@Composable
private fun SigningContent(content: String) {
    var isVisible by remember { mutableStateOf(false) }
    val jsonRoots = remember(content) { parseExpandableJson(content) }

    TextCell(
        modifier = Modifier.padding(vertical = 8.dp),
        title = {
            Row(
                modifier = Modifier.clickable { isVisible = !isVisible },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoonItemTitle(
                    modifier = Modifier.weight(1f),
                    text = "Signing content",
                    color = UIKit.colorScheme.text.secondary,
                )
                ExpandedIcon(isVisible)
            }
        },
        subtitle = {
            MoonExpandable(isVisible) {
                if (jsonRoots != null) {
                    ExpandableJsonList(roots = jsonRoots)
                } else {
                    MoonItemTitle(text = content, maxLines = 100)
                }
            }
        },
        minHeight = 56.dp,
        onClick = null,
    )
}

private fun Transaction.recipient(): String? = when (this) {
    is Transaction.Transfer -> to.display
    is Transaction.Call -> contract.display
    else -> null
}

// A call that moves no coin has no amount to show.
private fun Transaction.shownAmount(): BigInteger? = when {
    this is Transaction.Call && amount == BigInteger.ZERO -> null
    else -> amount
}
