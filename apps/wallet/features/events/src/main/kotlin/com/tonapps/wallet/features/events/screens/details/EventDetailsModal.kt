package com.tonapps.wallet.features.events.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.chainLabel
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.core.components.tokenDisplayName
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.features.events.components.NftCollectionRow
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.HistoryNft
import com.tonapps.wallet.features.events.protocolDisplayName
import com.tonapps.wallet.features.events.statusTitle
import com.tonapps.wallet.features.events.title
import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import io.walletapi.models.Chain
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import ui.components.base.UIKitProgressIndicator
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonCutRow
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.Shapes
import ui.theme.UIKit
import uikit.navigation.Navigation

@Suppress("UNUSED_PARAMETER")
@Composable
fun EventDetailsModal(
    event: HistoryEventEntity,
    wallet: McWalletEntity?,
    nft: HistoryNft? = null,
    onDismiss: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onDismiss)
    val clipboard = rememberClipboardManager()
    val context = LocalContext.current
    val isNft = event.isNftTransfer

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )

        TxDetailsHeader(
            event = event,
            nft = nft,
        )

        Spacer(Modifier.height(32.dp))

        val explorerTxId = remember(event) { event.explorerTransactionId() }
        MoonBundleCell {
            Column {
                val addressTitleRes: Int
                val address: String?
                if (event.activityType == ActivityType.swap) {
                    addressTitleRes = Localization.recipient
                    address = event.toAddress?.takeIf { it.isNotBlank() }
                        ?: event.walletAddress?.takeIf { it.isNotBlank() }
                } else {
                    val incoming = event.isIncomingLike
                    val counterparty = if (incoming) {
                        event.fromAddress
                    } else {
                        event.toAddress
                    }
                    address = counterparty?.takeIf { it.isNotBlank() }
                        ?: event.walletAddress?.takeIf { incoming && it.isNotBlank() }
                    addressTitleRes = if (incoming && counterparty.isNullOrBlank()) {
                        Localization.recipient_address
                    } else if (incoming) {
                        Localization.sender_address
                    } else {
                        Localization.recipient_address
                    }
                }
                if (address != null) {
                    CopyableValueCell(
                        title = stringResource(addressTitleRes),
                        value = address,
                        onCopy = { clipboard.copy(address) },
                    )
                    MoonItemDivider()
                }

                if (isNft && !event.comment.isNullOrBlank()) {
                    MoonPropertyBigCell(
                        title = stringResource(Localization.comment),
                        value = event.comment,
                        valueDescription = null,
                        onClick = { clipboard.copy(event.comment) },
                    )
                    MoonItemDivider()
                }

                val networkAsset = event.displayAsset()?.valueOrNull
                val isCrossChainSwap = event.isExchangeLike() &&
                    event.fromChain != event.toChain
                MoonPropertyBigCell(
                    title = stringResource(Localization.network),
                    value = if (isCrossChainSwap) {
                        "${event.fromChain.displayName()} → ${event.toChain.displayName()}"
                    } else {
                        networkAsset?.chain?.coin?.name
                            ?: event.networkChain().displayName()
                    },
                    valueDescription = if (isCrossChainSwap) {
                        "${event.fromChain.shortName()} → ${event.toChain.shortName()}"
                    } else if (isNft) {
                        event.networkChain().shortName()
                    } else {
                        networkAsset?.tokenDisplayName()
                    },
                )

                val tronResourceTexts = event.tronResourceFeeTexts()
                val feeText = event.feeText()
                when {
                    tronResourceTexts != null -> {
                        MoonItemDivider()
                        MoonPropertyBigCell(
                            title = stringResource(Localization.network_fee),
                            value = tronResourceTexts.first,
                            valueDescription = tronResourceTexts.second,
                        )
                    }
                    feeText != null -> {
                        MoonItemDivider()
                        MoonPropertyBigCell(
                            title = stringResource(Localization.network_fee),
                            value = feeText,
                            valueDescription = event.feeFiatText(),
                        )
                    }
                }

                if (explorerTxId != null) {
                    MoonItemDivider()
                    CopyableValueCell(
                        title = stringResource(Localization.tx_hash),
                        value = explorerTxId,
                        onCopy = { clipboard.copy(explorerTxId) },
                    )
                }

                val protocolName = protocolDisplayName(event.protocol)
                if (protocolName != null && !event.isDomainRenew) {
                    MoonItemDivider()
                    MoonPropertyBigCell(
                        title = stringResource(Localization.protocol),
                        value = protocolName,
                        valueDescription = null,
                    )
                }

                if (!isNft && !event.comment.isNullOrBlank()) {
                    MoonItemDivider()
                    MoonPropertyBigCell(
                        title = stringResource(Localization.comment),
                        value = event.comment,
                        valueDescription = null,
                        onClick = { clipboard.copy(event.comment) },
                    )
                }
            }
        }

        val explorerUrl = event.explorerUrl
        if (!explorerUrl.isNullOrBlank() && explorerTxId != null) {
            Spacer(Modifier.height(32.dp))
            TxExplorerChip(
                hash = explorerTxId.take(8),
                onOpen = {
                    Navigation.from(context)?.openURL(explorerUrl)
                    navigator.close()
                },
                onCopy = { clipboard.copy(explorerTxId) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// TODO to MoonChip
@Composable
private fun TxExplorerChip(
    hash: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onCopy)
            .height(36.dp)
            .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UIKitIcon.ic_globe_16),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = UIKit.colorScheme.buttonSecondary.primaryForeground,
        )

        Text(
            text = stringResource(Localization.transaction),
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.buttonSecondary.primaryForeground,
        )

        Text(
            text = hash,
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.tertiary,
        )
    }
}

private fun HistoryEventEntity.explorerTransactionId(): String? {
    val txIds = txIds.mapNotNull { raw ->
        raw.trim().takeIf { it.isNotEmpty() }
    }
    if (txIds.isEmpty()) {
        return null
    }

    val sourceChainId = fromChain.value
    val fromSourceChain = txIds.firstNotNullOfOrNull { txId ->
        val separator = txId.indexOf(':')
        if (separator <= 0) {
            return@firstNotNullOfOrNull null
        }
        val chain = txId.substring(0, separator)
        if (!chain.equals(sourceChainId, ignoreCase = true)) {
            return@firstNotNullOfOrNull null
        }
        txId.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
    }
    if (fromSourceChain != null) {
        return fromSourceChain
    }

    return txIds.firstNotNullOfOrNull { txId ->
        val separator = txId.indexOf(':')
        if (separator < 0) {
            txId
        } else {
            null
        }
    }
}

@Composable
private fun TxDetailsHeader(
    event: HistoryEventEntity,
    nft: HistoryNft?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            nft != null -> NftHeaderImage(imageUrl = nft.imageUrl)
            event.isExchangeLike() -> SwapHeaderImage(
                inAsset = event.inAsset.takeIfDisplayable(),
                outAsset = event.outAsset.takeIfDisplayable(),
            )
            else -> {
                val asset = event.displayAsset()
                if (asset != null) {
                    SingleAssetHeaderImage(asset = asset)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            nft != null -> {
                Text(
                    text = nft.name,
                    style = UIKit.typography.h2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                NftCollectionRow(
                    nft = nft,
                    textStyle = UIKit.typography.body1,
                    verifiedIconTint = UIKit.colorScheme.icon.secondary,
                    textAlign = TextAlign.Center,
                )
            }
            event.isExchangeLike() -> {
                SwapAmountLines(event)
                val fiat = event.swapFiatText()
                if (fiat != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = fiat,
                        style = UIKit.typography.body1,
                        color = UIKit.colorScheme.text.secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Text(
                    text = headerTitle(event),
                    style = UIKit.typography.h2,
                    textAlign = TextAlign.Center,
                )

                val secondary = headerSubtitle(event)
                if (secondary != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = secondary,
                        style = UIKit.typography.body1,
                        color = UIKit.colorScheme.text.secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = headerDate(event),
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
        } else if (event.isFailed) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Localization.failed),
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.accent.orange,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NftHeaderImage(imageUrl: String) {
    MoonItemImage(
        image = imageUrl,
        size = 96.dp,
        shape = Shapes.medium12,
    )
}

@Composable
private fun SwapAmountLines(event: HistoryEventEntity) {
    val out = event.outAmount(asset = event.outAsset)
    val into = event.inAmount(asset = event.inAsset)
    if (out != null) {
        Text(
            text = amountLine(
                amount = out,
                sign = "– ",
                chain = event.outAsset?.chainLabel(),
            ),
            style = UIKit.typography.h2,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    if (into != null) {
        Text(
            text = amountLine(
                amount = into,
                sign = "+ ",
                chain = event.inAsset?.chainLabel(),
            ),
            style = UIKit.typography.h2,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun amountLine(
    amount: String,
    sign: String,
    chain: String?,
): AnnotatedString {
    val primary = UIKit.colorScheme.text.primary
    val secondary = UIKit.colorScheme.text.secondary
    return buildAnnotatedString {
        withStyle(SpanStyle(color = primary)) {
            append(sign)
            append(amount)
        }
        if (!chain.isNullOrBlank()) {
            append(" ")
            withStyle(SpanStyle(color = secondary)) {
                append(chain)
            }
        }
    }
}

@Composable
private fun headerDate(event: HistoryEventEntity): String {
    val date = event.blockTime.formatFullDateTime()
    if (event.isExchangeLike()) {
        return if (event.isPending) {
            date
        } else {
            stringResource(Localization.swapped_on, date)
        }
    }
    return when (event.activityType) {
        ActivityType.dns_renew -> stringResource(Localization.renewed_on, date)
        ActivityType.stake -> stringResource(Localization.staked_on, date)
        ActivityType.unstake -> stringResource(Localization.unstake_on, date)
        ActivityType.burn -> stringResource(Localization.burned_on, date)
        ActivityType.receive, ActivityType.mint -> stringResource(Localization.received_on, date)
        ActivityType.send -> stringResource(Localization.sent_on, date)
        else -> if (event.isIncomingLike) {
            stringResource(Localization.received_on, date)
        } else {
            stringResource(Localization.sent_on, date)
        }
    }
}

@Composable
private fun SingleAssetHeaderImage(asset: AssetEntity) {
    val assetImageUrl = asset.assetImageUrl()
    val chainImageUrl = asset.tokenChainImageUrl()
    MoonCutBadgedBox(
        badge = if (chainImageUrl != null) {
            { MoonItemImage(image = chainImageUrl, size = 32.dp) }
        } else {
            null
        },
        direction = BadgeDirection.EndBottom,
    ) {
        MoonItemImage(image = assetImageUrl, size = 96.dp)
    }
}

@Composable
private fun SwapHeaderImage(
    inAsset: AssetEntity?,
    outAsset: AssetEntity?,
) {
    val outImage = outAsset?.assetImageUrl()
    val inImage = inAsset?.assetImageUrl()
    if (outImage == null && inImage == null) {
        return
    }

    MoonCutRow {
        if (outImage != null) {
            val outChainImage = outAsset?.tokenChainImageUrl()
            MoonCutBadgedBox(
                badge = if (outChainImage != null) {
                    { MoonItemImage(image = outChainImage, size = 24.dp) }
                } else {
                    null
                },
                direction = BadgeDirection.StartBottom,
            ) {
                MoonItemImage(image = outImage, size = 72.dp)
            }
        }
        if (inImage != null) {
            val inChainImage = inAsset?.tokenChainImageUrl()
            MoonCutBadgedBox(
                badge = if (inChainImage != null) {
                    { MoonItemImage(image = inChainImage, size = 24.dp) }
                } else {
                    null
                },
                direction = BadgeDirection.EndBottom,
            ) {
                MoonItemImage(image = inImage, size = 72.dp)
            }
        }
    }
}

private fun AssetEntity?.takeIfDisplayable(): AssetEntity? = this?.takeIf { it.valueOrNull != null }

private fun HistoryEventEntity.isExchangeLike(): Boolean = when (activityType) {
    ActivityType.swap -> true
    ActivityType.bridge ->
        inAsset.takeIfDisplayable() != null && outAsset.takeIfDisplayable() != null
    else -> false
}

private fun HistoryEventEntity.displayAsset(): AssetEntity? = when {
    activityType == ActivityType.swap -> outAsset ?: inAsset
    isDomainRenew -> outAsset ?: inAsset ?: feeAsset
    isIncomingLike -> {
        if (unitInAmount != null) {
            inAsset ?: outAsset
        } else {
            outAsset ?: inAsset
        }
    }
    else -> {
        if (unitOutAmount != null) {
            outAsset ?: inAsset
        } else {
            inAsset ?: outAsset
        }
    }
}.takeIfDisplayable()

private fun HistoryEventEntity.networkChain(): Chain =
    if (isIncomingLike) {
        toChain
    } else {
        fromChain
    }

private fun Chain.displayName(): String = when (this) {
    Chain.ton -> "TON"
    Chain.eth -> "Ethereum"
    Chain.base -> "Base"
    Chain.btc -> "Bitcoin"
    Chain.tron -> "Tron"
    Chain.arb -> "Arbitrum"
    Chain.bsc -> "BSC"
    Chain.sol -> "Solana"
}

private fun Chain.shortName(): String = when (this) {
    Chain.ton -> "TON"
    Chain.eth -> "ETH"
    Chain.base -> "Base"
    Chain.btc -> "BTC"
    Chain.tron -> "TRON"
    Chain.arb -> "ARB"
    Chain.bsc -> "BSC"
    Chain.sol -> "SOL"
}

@Composable
private fun headerTitle(event: HistoryEventEntity): AnnotatedString {
    if (event.isDomainRenew) {
        return AnnotatedString(event.renewedDomain ?: event.title())
    }
    val amount = if (event.isIncomingLike) {
        event.inAmount(asset = event.inAsset)
            ?: event.outAmount(asset = event.outAsset)
    } else {
        event.outAmount(asset = event.outAsset)
            ?: event.inAmount(asset = event.inAsset)
    }
    if (amount == null) {
        val text = if (event.isNftTransfer) {
            stringResource(Localization.nft)
        } else {
            event.title()
        }
        return AnnotatedString(text)
    }
    val sign = if (event.isIncomingLike) {
        "+ "
    } else {
        "– "
    }
    return amountLine(
        amount = amount,
        sign = sign,
        chain = event.displayAsset()?.chainLabel(),
    )
}

@Composable
private fun headerSubtitle(event: HistoryEventEntity): AnnotatedString? =
    event.amountFiatText()?.let(::AnnotatedString)

@Composable
private fun HistoryEventEntity.swapFiatText(): String? {
    val usd = inAmountUsd ?: outAmountUsd ?: return null
    return remember(usd) {
        CurrencyFormatter.formatFiat("USD", BigDecimal.valueOf(usd)).toString()
    }
}

@Composable
private fun HistoryEventEntity.amountFiatText(): String? {
    val usd = if (isIncomingLike) {
        inAmountUsd ?: outAmountUsd
    } else {
        outAmountUsd ?: inAmountUsd
    } ?: return null
    return remember(usd) {
        CurrencyFormatter.formatFiat("USD", BigDecimal.valueOf(usd)).toString()
    }
}

@Composable
private fun HistoryEventEntity.feeText(): String? {
    val value = unitFeeAmount ?: return null
    val asset = feeAsset?.valueOrNull ?: return null
    return remember(value, asset) { Formatter.formatShort(value = value, asset = asset) }
}

@Composable
private fun HistoryEventEntity.tronResourceFeeTexts(): Pair<String, String?>? {
    val resource = tronResource ?: return null
    if (isIncoming) {
        return null
    }
    if (unitFeeAmount?.value?.isZero() == false) {
        return null
    }
    val energyText = resource.energy.takeIf { it > 0 }
        ?.let { stringResource(Localization.tron_resource_energy, it.formatGrouped()) }
    val bandwidthText = resource.bandwidth.takeIf { it > 0 }
        ?.let { stringResource(Localization.tron_resource_bandwidth, it.formatGrouped()) }
    return when {
        energyText != null -> energyText to bandwidthText
        bandwidthText != null -> bandwidthText to null
        else -> null
    }
}

private fun Long.formatGrouped(): String = String.format(CurrencyFormatter.locale, "%,d", this)

@Composable
private fun HistoryEventEntity.feeFiatText(): CharSequence? {
    val usd = feeAmountUsd ?: return null
    return remember(usd) { CurrencyFormatter.formatFiat("USD", BigDecimal.valueOf(usd)) }
}

@Composable
private fun HistoryEventEntity.inAmount(asset: AssetEntity?): String? {
    val value = unitInAmount ?: return null
    val a = asset?.valueOrNull ?: return null
    return remember(value, a) { Formatter.formatShort(value = value, asset = a) }
}

@Composable
private fun HistoryEventEntity.outAmount(asset: AssetEntity?): String? {
    val value = unitOutAmount ?: return null
    val a = asset?.valueOrNull ?: return null
    return remember(value, a) { Formatter.formatShort(value = value, asset = a) }
}

@Composable
private fun CopyableValueCell(
    title: String,
    value: String,
    onCopy: () -> Unit,
) {
    TextCell(
        title = {
            MoonItemTitle(
                text = title,
                color = UIKit.colorScheme.text.secondary,
            )
        },
        subtitle = {
            Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        },
        minHeight = 76.dp,
        onClick = onCopy,
    )
}

// TODO move with some helper
@Composable
private fun OffsetDateTime.formatFullDateTime(): String {
    val zoned = atZoneSameInstant(ZoneId.systemDefault())
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(zoned)
}


// -----------------------------------------------------------------------------
// Previews
// -----------------------------------------------------------------------------

private fun previewWallet() = McWalletEntity(
    credentialId = "preview",
    name = "Main Wallet",
    emoji = "💎",
    type = McWalletType.Multicoin,
    id = ""
)

private fun previewAsset(symbol: String): AssetEntity = if (symbol == "TON") {
    AssetEntity(
        id = "ton/mainnet/coin",
        name = "Toncoin",
        symbol = "TON",
        decimals = 9,
        imageUrl = "",
    )
} else {
    AssetEntity(
        id = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
        name = symbol,
        symbol = symbol,
        decimals = 6,
        imageUrl = "",
    )
}

private fun previewActivity(type: ActivityType): HistoryEventEntity {
    val now = OffsetDateTime.now()
    return HistoryEventEntity(
        activityType = type,
        status = ActivityStatus.confirmed,
        blockTime = now,
        walletAddress = "UQA1ABCDEF1234567890MALX",
        fromAddress = "UQB1FROM23456789012345abcd",
        toAddress = "UQC1TO234567890123456abcdef",
        fromChain = Chain.ton,
        toChain = Chain.ton,
        inAmount = if (type != ActivityType.send) {
            "100000000"
        } else {
            null
        },
        outAmount = if (type != ActivityType.receive) {
            "100000000"
        } else {
            null
        },
        inAmountUsd = if (type != ActivityType.send) {
            12.5
        } else {
            null
        },
        outAmountUsd = if (type != ActivityType.receive) {
            12.5
        } else {
            null
        },
        feeAmount = "4200",
        feeAmountUsd = 0.03,
        inAsset = if (type != ActivityType.send) {
            previewAsset("USDT")
        } else {
            null
        },
        outAsset = if (type != ActivityType.receive) {
            previewAsset(
                if (type == ActivityType.swap) {
                    "TON"
                } else {
                    "USDT"
                },
            )
        } else {
            null
        },
        feeAsset = previewAsset("TON"),
        txIds = listOf("d2e1b9c4abf3a1d61a0c5fe9b2a3e4d50f6789abc1234567890def123456abcd"),
        blockNumber = 123_456_789L,
        protocol = if (type == ActivityType.swap) {
            "Ston.fi"
        } else {
            null
        },
        explorerUrl = "https://tonviewer.com/transaction/d2e1b9c4abf3a1d61a0c5fe9b2a3e4d5",
        comment = if (type == ActivityType.send) {
            "Thanks!"
        } else {
            null
        },
        direction = when (type) {
            ActivityType.receive -> ActivityDirection.`in`
            ActivityType.send -> ActivityDirection.out
            else -> ActivityDirection.self
        },
    )
}

@Preview
@Composable
private fun EventDetailsModalReceivePreview() {
    ThemedPreview {
        EventDetailsModal(
            event = previewActivity(ActivityType.receive),
            wallet = previewWallet(),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun EventDetailsModalSendPreview() {
    ThemedPreview {
        EventDetailsModal(
            event = previewActivity(ActivityType.send),
            wallet = previewWallet(),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun EventDetailsModalPendingPreview() {
    ThemedPreview {
        EventDetailsModal(
            event = previewActivity(ActivityType.send).copy(status = ActivityStatus.pending),
            wallet = previewWallet(),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun EventDetailsModalSwapPreview() {
    ThemedPreview {
        EventDetailsModal(
            event = previewActivity(ActivityType.swap).copy(
                fromChain = Chain.base,
                toChain = Chain.bsc,
                toAddress = "EQCONmollH5o17uo421u531UK4x7oLU",
                comment = null,
            ),
            wallet = previewWallet(),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun EventDetailsModalPendingSwapPreview() {
    ThemedPreview {
        EventDetailsModal(
            event = previewActivity(ActivityType.swap).copy(
                status = ActivityStatus.pending,
                inAmount = null,
                inAmountUsd = null,
            ),
            wallet = previewWallet(),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun EventDetailsModalPendingBridgePreview() {
    ThemedPreview {
        EventDetailsModal(
            event = previewActivity(ActivityType.bridge).copy(
                status = ActivityStatus.pending,
                fromChain = Chain.eth,
                toChain = Chain.tron,
                inAmount = null,
                inAmountUsd = null,
            ),
            wallet = previewWallet(),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun EventDetailsModalSpamNftPreview() {
    ThemedPreview {
        EventDetailsModal(
            event = previewActivity(ActivityType.receive).copy(
                inAsset = AssetEntity(
                    id = "ton/mainnet/nft/EQAvlWFDxGF2lXm67y4yzC17wYKD9A0guwPkMs1gOsM__NOT",
                    name = "",
                    symbol = "",
                    decimals = 0,
                    imageUrl = "",
                ),
                inAmountUsd = null,
                outAmountUsd = null,
                comment = "Claim 5,000 USDT at scam-site.example",
                isSpam = true,
            ),
            wallet = previewWallet(),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewTxExplorerChip() {
    ThemedPreview {
        TxExplorerChip("0xFFAFAFAFAFAFAFAFAFAFAFA", {}, {})
    }
}

@Preview
@Composable
private fun PreviewCopyableValueCell() {
    ThemedPreview {
        CopyableValueCell(
            title = "Tx hash",
            value = "d2e1b9c4abf3a1d61a0c5fe9b2a3e4d50f6789abc1234567890def123456abcd",
            onCopy = {},
        )
    }
}
