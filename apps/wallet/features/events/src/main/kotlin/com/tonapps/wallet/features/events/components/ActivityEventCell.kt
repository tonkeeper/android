package com.tonapps.wallet.features.events.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.components.coinShortName
import com.tonapps.extensions.short4
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.HistoryNft
import com.tonapps.wallet.features.events.formattedTime
import com.tonapps.wallet.features.events.hasStakingProviderIcon
import com.tonapps.wallet.features.events.iconPainter
import com.tonapps.wallet.features.events.stakingProviderName
import com.tonapps.wallet.features.events.statusTitle
import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityType
import ui.components.base.UIKitProgressIndicator
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonTextShimmer
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.preview.ThemedPreview
import ui.theme.UIKit

private const val AMOUNT_MIN_FONT_SCALE = 0.8f
private const val AMOUNT_FONT_SCALE_STEP = 0.95f

@Composable
fun ActivityEventCell(
    activity: HistoryEventEntity,
    nft: HistoryNft? = null,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: () -> Unit = {},
    onNftClick: (() -> Unit)? = null,
) {
    val subtitle = activity.stakingProviderName()
        ?: activity.counterpartyAddress?.short4
        ?: stringResource(Localization.unknown)
    val isNft = activity.isNftActivity
    val comment = if (nft != null && activity.isDomainRenew) {
        activity.comment?.takeIf { it.isNotBlank() }
    } else {
        activity.displayComment
    }

    MoonBundleCell(position = position, onClick = onClick) {
        TextCell(
            modifier = Modifier.padding(vertical = 8.dp),
            minHeight = 76.dp,
            verticalAlignment = Alignment.Top,
            image = { ActivityLeadingIcon(activity) },
            title = {
                ActivityTitleRow(
                    activity = activity,
                    isNft = isNft,
                )
            },
            subtitle = {
                ActivitySecondaryRow(
                    activity = activity,
                    subtitle = subtitle,
                    isNft = isNft,
                    nft = nft,
                    comment = comment,
                    onNftClick = onNftClick,
                )
            },
            content = null,
            onClick = null,
        )
    }
}

@Composable
private fun ActivityLeadingIcon(activity: HistoryEventEntity) {
    MoonCutBadgedBox(
        badge = if (activity.isPending) {
            { UIKitProgressIndicator() }
        } else {
            null
        },
        direction = BadgeDirection.StartTop,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(UIKit.colorScheme.background.contentTint),
            contentAlignment = Alignment.Center,
        ) {
            val useOriginalIcon = activity.hasStakingProviderIcon()
            Icon(
                painter = activity.iconPainter(),
                contentDescription = null,
                tint = if (useOriginalIcon) {
                    Color.Unspecified
                } else {
                    UIKit.colorScheme.icon.secondary
                },
            )
        }
    }
}

@Composable
private fun ActivityTitleRow(
    activity: HistoryEventEntity,
    isNft: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        MoonItemTitle(text = activity.statusTitle())
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            if (isNft) {
                Text(
                    text = stringResource(Localization.nft),
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                    maxLines = 1,
                )
            } else {
                ActivityAmountRows(activity)
            }
        }
    }
}

@Composable
private fun ActivitySecondaryRow(
    activity: HistoryEventEntity,
    subtitle: String,
    isNft: Boolean,
    nft: HistoryNft?,
    comment: String?,
    onNftClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MoonItemSubtitle(text = subtitle, maxLines = 1)
            if (activity.isFailed) {
                Text(
                    text = stringResource(Localization.failed),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.accent.orange,
                )
            }
            if (nft != null || comment != null) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (nft != null) {
                        ActivityNftPreview(nft = nft, onClick = onNftClick)
                    }
                    if (comment != null) {
                        ActivityCommentChip(text = comment)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = activity.formattedTime(),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
            )
            ActivityStatusLabel(activity = activity, isNft = isNft)
        }
    }
}

@Composable
private fun ActivityCommentChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(UIKit.colorScheme.background.contentTint)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = UIKit.typography.body2,
        color = UIKit.colorScheme.text.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ActivityStatusLabel(
    activity: HistoryEventEntity,
    isNft: Boolean,
) {
    if (activity.isSpam) {
        Text(
            text = stringResource(Localization.spam),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.tertiary,
        )
        return
    }
    if (isNft) {
        return
    }

    // enum declaration order is severity order: trusted < whitelist < none < blacklist
    val verification = maxOf(
        activity.inAsset?.takeIf { it.valueOrNull != null }?.verification
            ?: AssetEntity.Verification.whitelist,
        activity.outAsset?.takeIf { it.valueOrNull != null }?.verification
            ?: AssetEntity.Verification.whitelist,
    )
    when (verification) {
        AssetEntity.Verification.trusted,
        AssetEntity.Verification.whitelist -> Unit
        AssetEntity.Verification.none -> Text(
            text = stringResource(Localization.unverified_token),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.accent.orange,
        )
        AssetEntity.Verification.blacklist -> Text(
            text = stringResource(Localization.scam),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.accent.red,
        )
    }
}

@Composable
private fun ActivityAmountRows(activity: HistoryEventEntity) {
    when (activity.activityType) {
        ActivityType.stake, ActivityType.unstake, ActivityType.burn, ActivityType.send -> {
            if (activity.unitOutAmount != null) {
                OutAmountRow(activity)
            } else {
                AmountRow(
                    amount = activity.unitInAmount,
                    asset = activity.inAsset,
                    positive = false,
                )
            }
        }
        ActivityType.mint, ActivityType.receive -> {
            if (activity.unitInAmount != null) {
                InAmountRow(activity)
            } else {
                AmountRow(
                    amount = activity.unitOutAmount,
                    asset = activity.outAsset,
                    positive = true,
                )
            }
        }
        ActivityType.swap -> {
            InAmountRow(activity)
            OutAmountRow(activity)
        }
        ActivityType.dns_renew -> {
            AmountRow(
                amount = activity.unitFeeAmount,
                asset = activity.feeAsset,
                positive = false,
            )
        }
        else -> {
            InAmountRow(activity)
            OutAmountRow(activity)
        }
    }
}

@Composable
private fun InAmountRow(activity: HistoryEventEntity) {
    AmountRow(
        amount = activity.unitInAmount,
        asset = activity.inAsset,
        positive = true,
    )
}

@Composable
private fun OutAmountRow(activity: HistoryEventEntity) {
    AmountRow(
        amount = activity.unitOutAmount,
        asset = activity.outAsset,
        positive = false,
    )
}

@Composable
private fun AmountRow(
    amount: BaseUnit?,
    asset: AssetEntity?,
    positive: Boolean,
) {
    val value = amount ?: return
    val resolved = asset?.valueOrNull ?: return

    val formatted = remember(value, resolved) {
        Formatter.formatShort(value = value, asset = resolved)
    }

    val chainLabel = resolved.coinShortName()
    val amountColor = if (positive) {
        UIKit.colorScheme.accent.green
    } else {
        UIKit.colorScheme.text.primary
    }
    val secondaryColor = UIKit.colorScheme.text.secondary
    val sign = if (positive) {
        "+"
    } else {
        "–"
    }
    val text = remember(formatted, chainLabel, amountColor, secondaryColor, sign) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = amountColor)) {
                append(sign)
                append(" ")
                append(formatted)
            }
            if (chainLabel != null) {
                append(" ")
                withStyle(SpanStyle(color = secondaryColor)) {
                    append(chainLabel)
                }
            }
        }
    }

    var fontScale by remember(text) { mutableStateOf(1f) }
    var readyToDraw by remember(text) { mutableStateOf(false) }
    val style = UIKit.typography.label1
    Text(
        modifier = Modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        text = text,
        style = style.copy(fontSize = style.fontSize * fontScale),
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontScale > AMOUNT_MIN_FONT_SCALE) {
                fontScale = (fontScale * AMOUNT_FONT_SCALE_STEP)
                    .coerceAtLeast(AMOUNT_MIN_FONT_SCALE)
            } else {
                readyToDraw = true
            }
        },
    )
}

@Composable
fun ActivityEventCell(
    position: MoonBundlePosition,
    modifier: Modifier = Modifier,
) {
    val backgroundFill = UIKit.colorScheme.background.contentTint
    MoonBundleCell(
        modifier = modifier,
        position = position,
    ) {
        TextCell(
            modifier = Modifier.padding(vertical = 8.dp),
            minHeight = 76.dp,
            verticalAlignment = Alignment.Top,
            image = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(UIKit.colorScheme.background.contentTint)
                )
            },
            title = {
                MoonTextShimmer(
                    backgroundFill = backgroundFill,
                    text = "Received",
                    style = UIKit.typography.label1,
                )
            },
            subtitle = {
                MoonTextShimmer(
                    backgroundFill = backgroundFill,
                    text = "UQA1…MALX",
                    style = UIKit.typography.body2,
                )
            },
            content = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MoonTextShimmer(
                        backgroundFill = backgroundFill,
                        text = "+ 999 USDT ETH",
                        style = UIKit.typography.label1,
                    )
                    MoonTextShimmer(
                        backgroundFill = backgroundFill,
                        text = "17:32",
                        style = UIKit.typography.body2,
                    )
                }
            },
            onClick = null,
        )
    }
}

@Preview
@Composable
private fun ActivityEventCellRegularPreview() {
    ThemedPreview {
        Column {
            ActivityEventCell(
                activity = previewShortReceive(),
                position = MoonBundlePosition.Header,
            )
            ActivityEventCell(
                activity = previewSwap(),
                position = MoonBundlePosition.Middle,
            )
            ActivityEventCell(
                activity = previewFailedSend(),
                position = MoonBundlePosition.Footer,
            )
        }
    }
}

@Preview
@Composable
private fun ActivityEventCellLongAmountPreview() {
    ThemedPreview {
        Column {
            ActivityEventCell(
                activity = previewLongAmountSend(),
                position = MoonBundlePosition.Header,
            )
            ActivityEventCell(
                activity = previewSpamReceive(),
                position = MoonBundlePosition.Footer,
            )
        }
    }
}

@Preview
@Composable
private fun ActivityEventCellLabelsPreview() {
    ThemedPreview {
        Column {
            ActivityEventCell(
                activity = previewUnverifiedReceive(),
                position = MoonBundlePosition.Header,
            )
            ActivityEventCell(
                activity = previewCommentSend(),
                position = MoonBundlePosition.Middle,
            )
            ActivityEventCell(
                activity = previewDomainRenew(),
                position = MoonBundlePosition.Middle,
            )
            ActivityEventCell(
                activity = previewDomainRenew(),
                nft = PREVIEW_DOMAIN_NFT,
                position = MoonBundlePosition.Middle,
            )
            ActivityEventCell(
                activity = previewNftReceive(),
                nft = PREVIEW_NFT,
                position = MoonBundlePosition.Footer,
            )
        }
    }
}

@Preview
@Composable
private fun ActivityEventCellPendingPreview() {
    ThemedPreview {
        Column {
            ActivityEventCell(
                activity = previewPendingSend(),
                position = MoonBundlePosition.Header,
            )
            ActivityEventCell(
                activity = previewPendingReceive(),
                position = MoonBundlePosition.Middle,
            )
            ActivityEventCell(
                activity = previewPendingSwap(),
                position = MoonBundlePosition.Middle,
            )
            ActivityEventCell(
                activity = previewFailedSend(),
                position = MoonBundlePosition.Footer,
            )
        }
    }
}

@Preview(fontScale = 1.5f, widthDp = 320, locale = "bg")
@Composable
private fun ActivityEventCellLongAmountLargeFontPreview() {
    ThemedPreview {
        Column {
            ActivityEventCell(
                activity = previewLongAmountSend(),
                position = MoonBundlePosition.Header,
            )
            ActivityEventCell(
                activity = previewSpamReceive(),
                position = MoonBundlePosition.Footer,
            )
        }
    }
}
