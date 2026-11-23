package com.tonapps.swap.status

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonapps.core.components.AssetImage
import com.tonapps.core.components.feeTexts
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.TransactionDateFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.wallet.data.multichain.exchange.status.SentSwap
import com.tonapps.wallet.data.multichain.exchange.status.SwapDetails
import com.tonapps.wallet.data.multichain.exchange.status.SwapNetworkFee
import com.tonapps.wallet.data.multichain.exchange.status.SwapStatus
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import io.walletapi.models.Chain
import io.walletapi.models.ActivityExplorerLink
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.MoonLoader
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit
import uikit.navigation.Navigation

@Composable
fun SwapDetailsModal(details: SwapDetails, onClose: () -> Unit) {
    val navigator = rememberDialogNavigator(onClose)
    val context = LocalContext.current
    MoonModalDialog(navigator = navigator) {
        SwapDetailsContent(
            details = details,
            onOpen = { url ->
                Navigation.from(context)?.openURL(url)
                navigator.close()
            },
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun SwapDetailsContent(details: SwapDetails, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val colors = UIKit.colorScheme
    val (status, statusColor) = when (details.status) {
        SwapStatus.Pending -> Localization.swap_details_pending to colors.text.secondary
        SwapStatus.Done -> Localization.done to colors.accent.green
        SwapStatus.Failed -> Localization.failed to colors.accent.orange
    }

    Column {
        MoonTopAppBarSimple(
            title = stringResource(Localization.swap_details_title),
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = onClose,
            backgroundColor = Color.Transparent,
        )
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountCard(stringResource(Localization.swap_details_you_send), details.swap.sell, details.sendAmount)
                    AmountCard(stringResource(Localization.swap_details_you_receive), details.swap.buy, details.receiveAmount)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .background(colors.background.page, CircleShape)
                        .padding(4.dp),
                ) {
                    MoonCircleIcon(
                        painter = painterResource(UIKitIcon.ic_arrow_down_16),
                        size = 40.dp,
                        color = colors.buttonTertiary.primaryBackground,
                        tint = colors.buttonTertiary.primaryForeground,
                    )
                }
            }
            MoonBundleCell(margins = PaddingValues()) {
                Column {
                    MoonPropertyBigCell(
                        title = stringResource(Localization.swap_details_date),
                        value = TransactionDateFormatter.formatWithYear(details.date, LocalConfiguration.current.locales[0]),
                        valueDescription = null,
                    )
                    val feeTexts = when (val fee = details.networkFee) {
                        null -> null
                        is SwapNetworkFee.Charges -> pluralStringResource(
                            Plurals.battery_charges,
                            fee.count,
                            String.format(CurrencyFormatter.locale, "%d", fee.count),
                        ) to null
                        is SwapNetworkFee.Amount -> fee.text to null
                        is SwapNetworkFee.TronResource -> fee.resource.feeTexts()?.let { it.first to null }
                    }
                    feeTexts?.let { texts ->
                        MoonItemDivider()
                        MoonPropertyBigCell(
                            title = stringResource(Localization.network_fee),
                            value = texts.first,
                            valueDescription = texts.second,
                        )
                    }
                    MoonItemDivider()
                    MoonPropertyBigCell(
                        title = { MoonLargeItemSubtitle(text = stringResource(Localization.swap_details_status)) },
                        content = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MoonItemTitle(text = stringResource(status), color = statusColor)
                                if (details.status == SwapStatus.Pending) {
                                    MoonLoader(modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                    )
                }
            }
            if (details.sent != null || details.received != null) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    details.sent?.let { TxChip(stringResource(Localization.sent), it, onOpen) }
                    if (details.receiving) {
                        Chip {
                            MoonLoader(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(Localization.receiving),
                                style = UIKit.typography.label2,
                                color = colors.text.secondary,
                                maxLines = 1,
                            )
                        }
                    }
                    details.received?.let { TxChip(stringResource(Localization.received), it, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun AmountCard(label: String, asset: AssetEntity, amount: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetImage(asset = asset, size = 44.dp)
        Column {
            Text(text = label, style = UIKit.typography.body2, color = UIKit.colorScheme.text.secondary)
            Text(
                text = amount,
                style = UIKit.typography.num2,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TxChip(label: String, link: ActivityExplorerLink, onOpen: (String) -> Unit) {
    val clipboard = rememberClipboardManager()
    val hash = link.txId.substringAfter(':')
    Chip(Modifier.combinedClickable(onClick = { onOpen(link.url) }, onLongClick = { clipboard.copy(hash) })) {
        Icon(
            painter = painterResource(UIKitIcon.ic_globe_16),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = UIKit.colorScheme.buttonSecondary.primaryForeground,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.buttonSecondary.primaryForeground,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = hash.take(8), style = UIKit.typography.label2, color = UIKit.colorScheme.text.tertiary, maxLines = 1)
    }
}

@Composable
private fun Chip(clickModifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
            .then(clickModifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Preview
@Composable
private fun SwapDetailsCrossChainPreview() {
    ThemedPreview {
        Column {
            SwapDetailsContent(
                details = previewDetails(SwapStatus.Pending, received = null, receiving = true, fee = null),
                onOpen = {},
                onClose = {},
            )
            SwapDetailsContent(
                details = previewDetails(SwapStatus.Done, received = PreviewReceived, fee = SwapNetworkFee.Charges(7)),
                onOpen = {},
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun SwapDetailsFailedPreview() {
    ThemedPreview {
        Column {
            SwapDetailsContent(
                details = previewDetails(SwapStatus.Failed, received = null, fee = SwapNetworkFee.Amount("0.03 GRAM")),
                onOpen = {},
                onClose = {},
            )
            SwapDetailsContent(
                details = previewDetails(SwapStatus.Done, received = null, fee = null),
                onOpen = {},
                onClose = {},
            )
        }
    }
}

private val PreviewReceived = ActivityExplorerLink(
    txId = "9b7c3e52a1",
    chain = Chain.eth,
    url = "https://etherscan.io/tx/0x9b7c3e52a1",
)

private fun previewDetails(
    status: SwapStatus,
    received: ActivityExplorerLink?,
    fee: SwapNetworkFee?,
    receiving: Boolean = false,
): SwapDetails {
    val gram = AssetEntity(id = "ton/mainnet/coin", name = "Gram", symbol = "GRAM", decimals = 9, imageUrl = "")
    val eth = AssetEntity(id = "eth/mainnet/coin", name = "Ethereum", symbol = "ETH", decimals = 18, imageUrl = "")
    return SwapDetails(
        swap = SentSwap(
            walletId = "wallet",
            txHash = "4d1e1608",
            sell = gram,
            sendAmount = BigInteger.parseString("100000000000"),
            buy = eth,
            receiveAmount = BigInteger.parseString("30000000000000000"),
        ),
        status = status,
        sendAmount = "100 GRAM",
        receiveAmount = "0.03 ETH",
        date = 1_793_376_454_000,
        sent = ActivityExplorerLink(txId = "4d1e1608ab", chain = Chain.ton, url = "https://tonviewer.com/4d1e1608ab"),
        received = received,
        receiving = receiving,
        networkFee = fee,
    )
}
