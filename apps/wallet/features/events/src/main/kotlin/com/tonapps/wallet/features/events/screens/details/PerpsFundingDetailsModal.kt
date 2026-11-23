package com.tonapps.wallet.features.events.screens.details

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.core.extensions.externalDrawableUrl
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.perps.data.formatTokenAmount
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.funding.PerpsFundingAssetId
import com.tonapps.perps.funding.PerpsWithdrawService
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.features.events.components.previewPerpsDeposit
import com.tonapps.wallet.features.events.components.previewPerpsDroppedDeposit
import com.tonapps.wallet.features.events.components.previewPerpsWithdrawal
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.PerpsBalanceChangeStatus
import com.tonapps.wallet.features.events.data.PerpsDepositOrigin
import com.tonapps.wallet.features.events.data.PerpsEventKind
import com.tonapps.wallet.features.events.data.PerpsResultEntity
import com.tonapps.wallet.features.events.formatted
import com.tonapps.wallet.features.events.perpsBalanceChangeTitleRes
import com.tonapps.wallet.features.events.protocolDisplayName
import com.tonapps.wallet.features.events.statusTitle
import com.tonapps.wallet.features.events.title
import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityStatus
import java.math.BigDecimal
import ui.components.base.UIKitProgressIndicator
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonCutRow
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import uikit.navigation.Navigation

private const val SHORT_HASH_PART_LENGTH = 8
private val ROW_HEIGHT = 56.dp

@Composable
internal fun PerpsFundingDetailsModal(
    event: HistoryEventEntity,
    wallet: McWalletEntity?,
    depositOrigin: PerpsDepositOrigin?,
    hiddenBalance: Boolean,
    onDismiss: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onDismiss)
    val clipboard = rememberClipboardManager()
    val context = LocalContext.current
    val isDeposit = event.perpsKind == PerpsEventKind.Deposit
    val transactionId = remember(event) { event.perpsFundingTransactionId() }
    val rows = remember(event, hiddenBalance, transactionId, wallet) {
        if (isDeposit) {
            event.perpsDepositRows(walletName = wallet?.name, hiddenBalance = hiddenBalance, transactionId = transactionId)
        } else {
            event.perpsFundingRows(hiddenBalance = hiddenBalance, transactionId = transactionId)
        }
    }

    MoonModalDialog(navigator = navigator) {
        MoonActionIcon(
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 16.dp, end = 16.dp)
                .semantics {
                    testTagsAsResourceId = true
                    testTag = "bottom_sheet_close"
                },
            painter = painterResource(UIKitIcon.ic_close_16),
            onClick = { navigator.close() },
            tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
            contentDescription = stringResource(Localization.close),
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isDeposit) {
                PerpsDepositHeader(event = event, origin = depositOrigin, hiddenBalance = hiddenBalance)
            } else {
                PerpsFundingHeader(event = event, hiddenBalance = hiddenBalance)
            }

            Spacer(Modifier.height(32.dp))
            PerpsFundingRows(
                rows = rows,
                onCopy = { clipboard.copy(it) },
            )

            val explorerUrl = event.explorerUrl
            if (!explorerUrl.isNullOrBlank() && transactionId != null) {
                Spacer(Modifier.height(32.dp))
                MoonAccentButton(
                    text = stringResource(
                        Localization.history_perps_transaction_button,
                        transactionId.take(SHORT_HASH_PART_LENGTH),
                    ),
                    size = ButtonSizeSmall,
                    buttonColors = ButtonColorsSecondary,
                    icon = {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_globe_16),
                            size = 16.dp,
                            color = UIKit.colorScheme.buttonSecondary.primaryForeground,
                        )
                    },
                    onClick = {
                        Navigation.from(context)?.openURL(explorerUrl)
                        navigator.close()
                    },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PerpsFundingHeader(
    event: HistoryEventEntity,
    hiddenBalance: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SingleAssetHeaderImage(asset = event.perpsFundingImageAsset())

        Spacer(Modifier.height(20.dp))
        Text(
            text = event.perpsHeadline(hiddenBalance) ?: event.title(),
            style = UIKit.typography.h2,
            textAlign = TextAlign.Center,
        )

        PerpsFundingDate(event)
    }
}

@Composable
private fun PerpsDepositHeader(
    event: HistoryEventEntity,
    origin: PerpsDepositOrigin?,
    hiddenBalance: Boolean,
) {
    val context = LocalContext.current
    val balanceImage = remember(context) { context.externalDrawableUrl(UIKitIcon.ic_usdc) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (origin != null) {
            MoonCutRow(overlap = 8.dp, cutPadding = 4.dp) {
                DepositSourceImage(asset = origin.asset)
                MoonItemImage(image = balanceImage, size = 72.dp)
            }
        } else {
            MoonItemImage(image = balanceImage, size = 72.dp)
        }

        Spacer(Modifier.height(20.dp))
        if (origin != null) {
            Text(
                text = if (hiddenBalance) {
                    HIDDEN_BALANCE
                } else {
                    CurrencyFormatter.PREFIX_MINUS + formatTokenAmount(origin.amount, origin.asset.symbol, origin.asset.decimals)
                },
                style = UIKit.typography.h2,
                textAlign = TextAlign.Center,
            )
        }
        val deposited = event.perpsResult
        if (deposited != null) {
            Text(
                text = if (hiddenBalance) {
                    AnnotatedString(HIDDEN_BALANCE)
                } else {
                    depositedLine(deposited)
                },
                style = UIKit.typography.h2,
                textAlign = TextAlign.Center,
            )
        }

        val headline = event.perpsHeadline(hiddenBalance)
        if (headline != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = headline,
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.Center,
            )
        }

        PerpsFundingDate(event)
    }
}

@Composable
private fun DepositSourceImage(asset: AssetEntity) {
    val chainImageUrl = asset.tokenChainImageUrl()
    MoonCutBadgedBox(
        badge = if (chainImageUrl != null) {
            { MoonItemImage(image = chainImageUrl, size = 24.dp) }
        } else {
            null
        },
        direction = BadgeDirection.StartBottom,
    ) {
        MoonItemImage(image = asset.assetImageUrl(), size = 72.dp)
    }
}

@Composable
private fun depositedLine(deposited: PerpsResultEntity): AnnotatedString {
    val primary = UIKit.colorScheme.text.primary
    val secondary = UIKit.colorScheme.text.secondary
    val amount = deposited.tokenAmountText().removeSuffix(" ${deposited.symbol}")
    return buildAnnotatedString {
        withStyle(SpanStyle(color = primary)) {
            append(CurrencyFormatter.PREFIX_PLUS)
            append(amount)
        }
        withStyle(SpanStyle(color = secondary)) {
            append(" ")
            append(deposited.symbol)
        }
    }
}

@Composable
private fun PerpsFundingDate(event: HistoryEventEntity) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(
            Localization.history_perps_event_on,
            event.perpsBalanceChangeTitleRes()?.let { stringResource(it) } ?: event.title(),
            event.blockTime.formatFullDateTime(),
        ),
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.text.secondary,
        textAlign = TextAlign.Center,
    )

    if (event.isPending) {
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event.statusTitle(),
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.text.secondary,
            )
            UIKitProgressIndicator(
                size = 14.dp,
                backgroundColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun PerpsFundingRows(
    rows: List<PerpsFundingRow>,
    onCopy: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        return
    }
    MoonBundleCell {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    MoonItemDivider()
                }
                Box(
                    modifier = Modifier.heightIn(min = ROW_HEIGHT),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    when (row) {
                        is PerpsFundingRow.Status -> PerpsFundingStatusRow(status = row.status)
                        is PerpsFundingRow.Wallet -> MoonPropertyBigCell(
                            title = stringResource(Localization.wallet),
                            value = row.name,
                            valueDescription = null,
                        )
                        PerpsFundingRow.DepositedTo -> MoonPropertyBigCell(
                            title = stringResource(Localization.history_perps_deposited_to),
                            value = stringResource(Localization.perps_balance),
                            valueDescription = null,
                        )
                        is PerpsFundingRow.Amount -> MoonPropertyBigCell(
                            title = stringResource(row.titleRes),
                            value = row.value,
                            valueDescription = row.fiat,
                        )
                        is PerpsFundingRow.Fee -> MoonPropertyBigCell(
                            title = stringResource(Localization.network_fee),
                            value = row.value,
                            valueDescription = null,
                        )
                        is PerpsFundingRow.TransactionHash -> MoonPropertyBigCell(
                            title = stringResource(Localization.history_perps_tx_hash),
                            value = row.hash.shortHash(),
                            valueDescription = null,
                            onClick = { onCopy(row.hash) },
                        )
                        is PerpsFundingRow.Protocol -> MoonPropertyBigCell(
                            title = stringResource(Localization.protocol),
                            value = protocolDisplayName(row.protocol, isPerps = true).orEmpty(),
                            valueDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerpsFundingStatusRow(status: PerpsBalanceChangeStatus) {
    val (textRes, color) = when (status) {
        PerpsBalanceChangeStatus.Done -> Localization.done to UIKit.colorScheme.accent.green
        PerpsBalanceChangeStatus.Failed -> Localization.failed to UIKit.colorScheme.accent.red
        PerpsBalanceChangeStatus.Dropped -> Localization.history_perps_dropped to UIKit.colorScheme.accent.red
    }
    MoonPropertyBigCell(
        title = {
            MoonLargeItemSubtitle(text = stringResource(Localization.history_perps_status))
        },
        content = {
            MoonItemTitle(text = stringResource(textRes), color = color)
        },
    )
}

private sealed interface PerpsFundingRow {
    data class Status(val status: PerpsBalanceChangeStatus) : PerpsFundingRow
    data class Wallet(val name: String) : PerpsFundingRow
    data object DepositedTo : PerpsFundingRow
    data class Amount(val titleRes: Int, val value: String, val fiat: String?) : PerpsFundingRow
    data class Fee(val value: String) : PerpsFundingRow
    data class TransactionHash(val hash: String) : PerpsFundingRow
    data class Protocol(val protocol: String) : PerpsFundingRow
}

private fun HistoryEventEntity.perpsFundingRows(
    hiddenBalance: Boolean,
    transactionId: String?,
): List<PerpsFundingRow> = listOfNotNull(
    perpsBalanceChangeStatus?.let(PerpsFundingRow::Status),
    perpsFundingAmountRow(hiddenBalance),
    perpsFeeRow(hiddenBalance),
    transactionId?.let(PerpsFundingRow::TransactionHash),
    protocol?.takeIf { it.isNotBlank() }?.let(PerpsFundingRow::Protocol),
)

private fun HistoryEventEntity.perpsDepositRows(
    walletName: String?,
    hiddenBalance: Boolean,
    transactionId: String?,
): List<PerpsFundingRow> = listOfNotNull(
    perpsBalanceChangeStatus?.takeIf { it != PerpsBalanceChangeStatus.Done }?.let(PerpsFundingRow::Status),
    walletName?.takeIf { it.isNotBlank() }?.let(PerpsFundingRow::Wallet),
    PerpsFundingRow.DepositedTo.takeIf { perpsBalanceChangeStatus == PerpsBalanceChangeStatus.Done },
    protocol?.takeIf { it.isNotBlank() }?.let(PerpsFundingRow::Protocol),
    perpsFeeRow(hiddenBalance),
    transactionId?.let(PerpsFundingRow::TransactionHash),
)

private fun HistoryEventEntity.perpsFeeRow(hiddenBalance: Boolean): PerpsFundingRow.Fee? = perpsFee?.let { fee ->
    PerpsFundingRow.Fee(
        if (hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            fee.formatted()
        },
    )
}

private fun HistoryEventEntity.perpsFundingAmountRow(hiddenBalance: Boolean): PerpsFundingRow.Amount? {
    val (titleRes, leg) = when (perpsKind) {
        PerpsEventKind.Deposit -> Localization.history_perps_you_deposit to perpsResult
        PerpsEventKind.Withdrawal -> Localization.perps_withdraw_you_receive to perpsReceived
        is PerpsEventKind.Opened, is PerpsEventKind.Increased, is PerpsEventKind.Reduced,
        is PerpsEventKind.Closed, is PerpsEventKind.Liquidated,
        PerpsEventKind.TakeProfit, PerpsEventKind.StopLoss, null -> return null
    }
    leg ?: return null
    if (hiddenBalance) {
        return PerpsFundingRow.Amount(titleRes = titleRes, value = HIDDEN_BALANCE, fiat = null)
    }
    return PerpsFundingRow.Amount(
        titleRes = titleRes,
        value = leg.tokenAmountText(),
        fiat = inAmountUsd?.let { CurrencyFormatter.formatFiat("USD", BigDecimal.valueOf(it)).toString() },
    )
}

private fun PerpsResultEntity.tokenAmountText(): String =
    formatTokenAmount(value.value, symbol, value.decimals)

@Composable
private fun HistoryEventEntity.perpsFundingImageAsset(): AssetEntity {
    val received = perpsReceivedAsset
    if (received != null) {
        return received
    }
    val context = LocalContext.current
    return remember(context) { perpsBalanceAsset(context) }
}

private fun perpsBalanceAsset(context: Context) = AssetEntity(
    id = PerpsFundingAssetId.USDC_ETHEREUM,
    name = PerpsWithdrawService.USDC_SYMBOL,
    symbol = PerpsWithdrawService.USDC_SYMBOL,
    decimals = PerpsFundingAmount.USDC_DECIMALS,
    imageUrl = context.externalDrawableUrl(UIKitIcon.ic_usdc),
)

internal fun HistoryEventEntity.perpsFundingTransactionId(): String? =
    explorerTransactionId() ?: txIds.firstNotNullOfOrNull { txId ->
        txId.substringAfter(':').trim().takeIf { it.isNotEmpty() }
    }

private fun String.shortHash(): String {
    if (length <= SHORT_HASH_PART_LENGTH * 2 + 1) {
        return this
    }
    return take(SHORT_HASH_PART_LENGTH) + "…" + takeLast(SHORT_HASH_PART_LENGTH)
}

@Preview
@Composable
private fun PerpsFundingDepositPreview() {
    ThemedPreview {
        PerpsFundingDetailsModal(
            event = previewPerpsDeposit().copy(txIds = listOf("lighter:0xffa77ca60ca81f2b7a47effda4e1b1654c2dcbec9bcb49ce947d5dc687a14f44")),
            wallet = null,
            depositOrigin = null,
            hiddenBalance = false,
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsFundingDepositFromTokenPreview() {
    ThemedPreview {
        PerpsFundingDetailsModal(
            event = previewPerpsDeposit().copy(txIds = listOf("lighter:0xffa77ca60ca81f2b7a47effda4e1b1654c2dcbec9bcb49ce947d5dc687a14f44")),
            wallet = null,
            depositOrigin = PerpsDepositOrigin(
                asset = AssetEntity(id = "ton", name = "GRAM", symbol = "GRAM", decimals = 9, imageUrl = ""),
                amount = BigDecimal("350"),
            ),
            hiddenBalance = false,
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsFundingDroppedDepositPreview() {
    ThemedPreview {
        PerpsFundingDetailsModal(
            event = previewPerpsDroppedDeposit(),
            wallet = null,
            depositOrigin = null,
            hiddenBalance = false,
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsFundingWithdrawalPreview() {
    ThemedPreview {
        PerpsFundingDetailsModal(
            event = previewPerpsWithdrawal().copy(
                txIds = listOf("ton:82ac91191c69b7aa8ab0a7e807a8b69e7812c67102faabcc9142de7b9cbe0075"),
                explorerUrl = "https://tonviewer.com/transaction/82ac91191c69b7aa8ab0a7e807a8b69e7812c67102faabcc9142de7b9cbe0075",
            ),
            wallet = null,
            depositOrigin = null,
            hiddenBalance = false,
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsFundingPendingWithdrawalPreview() {
    ThemedPreview {
        PerpsFundingDetailsModal(
            event = previewPerpsWithdrawal(status = ActivityStatus.pending),
            wallet = null,
            depositOrigin = null,
            hiddenBalance = false,
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PerpsFundingHiddenBalancePreview() {
    ThemedPreview {
        PerpsFundingDetailsModal(
            event = previewPerpsWithdrawal(),
            wallet = null,
            depositOrigin = null,
            hiddenBalance = true,
            onDismiss = {},
        )
    }
}
