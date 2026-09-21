package com.tonapps.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetWithDetails
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonVerificationBadge
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.painterResource
import ui.theme.UIKit
import ui.theme.modifiers.shimmer

@Composable
private fun AccountRow(
    content: (@Composable RowScope.() -> Unit),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun AccountTitle(
    asset: AssetEntity,
    tags: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val chainLabel = asset.chainLabel()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonItemTitle(
            text = asset.symbol,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (asset.verification == AssetEntity.Verification.trusted) {
            MoonVerificationBadge()
        }
        chainLabel?.let {
            MoonLabel(text = asset.value.coin.name)
        }
        tags?.invoke(this)
    }
}

@Composable
private fun VerificationSubtitle(verification: AssetEntity.Verification) {
    when (verification) {
        AssetEntity.Verification.trusted -> Unit
        AssetEntity.Verification.whitelist -> Unit
        AssetEntity.Verification.none -> MoonItemSubtitle(
            text = stringResource(Localization.unverified_token),
            color = UIKit.colorScheme.accent.orange,
        )
        AssetEntity.Verification.blacklist -> MoonItemSubtitle(
            text = stringResource(Localization.scam),
            color = UIKit.colorScheme.accent.red,
        )
    }
}

@Composable
fun AccountCell(
    asset: AssetEntity,
    title: (@Composable () -> Unit),
    subtitle: (@Composable () -> Unit),
    tags: (@Composable RowScope.() -> Unit)? = null,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: () -> Unit = {},
    image: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    MoonBundleCell(position = position) {
        TextCell(
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    title()
                    tags?.invoke(this)
                }
            },
            subtitle = subtitle,
            content = content,
            image = image ?: { AssetImage(asset) },
            onClick = onClick,
            minHeight = 76.dp,
        )
    }
}

@Composable
fun AccountCell(
    account: AccountWithDetails,
    tags: (@Composable RowScope.() -> Unit)? = null,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: () -> Unit = {},
    label: String? = null,
) {
    val formattedBalance = remember(account.displayBalance) {
        Formatter.formatShort(value = account.displayBalance)
    }

    val formattedPrice = remember(account.rate) {
        account.rate?.let {
            Formatter.formatFiat(
                value = account.asset.one,
                rate = it.value
            )
        }
    }

    val formattedFiat = remember(account.displayBalance, account.rate) {
        account.rate?.let {
            Formatter.formatFiat(
                value = account.displayBalance,
                rate = it.value
            )
        }
    }

    val priceDiff = account.rate?.percentChange24h

    AccountCell(
        asset = account.asset,
        title = {
            AccountRow {
                AccountTitle(
                    asset = account.asset,
                    modifier = Modifier.weight(1f),
                    tags = tags,
                )
                Spacer(modifier = Modifier.width(16.dp))
                MoonItemTitle(text = formattedBalance)
            }
        },
        subtitle = {
            AccountRow {
                Row {
                    if (account.asset.verification.isVerified) {
                        formattedPrice?.let {
                            MoonItemSubtitle(text = it)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        priceDiff?.let {
                            MoonItemSubtitle(
                                text = it,
                                color = it.percentDiffColor(),
                            )
                        }
                    } else {
                        VerificationSubtitle(account.asset.verification)
                    }
                }
                formattedFiat?.let { MoonItemSubtitle(text = it) }
            }
        },
        position = position,
        onClick = onClick,
    )
}

@Composable
fun AccountCell(
    account: AccountWithDetails,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    showVerification: Boolean = false,
    onClick: () -> Unit,
    content: (@Composable () -> Unit),
) {
    val formattedBalance = remember(account.displayBalance) {
        Formatter.formatShort(value = account.displayBalance)
    }

    val formattedFiat = remember(account.displayBalance, account.rate) {
        account.rate?.let {
            Formatter.formatFiat(value = account.displayBalance, rate = it.value)
        }
    }

    val subtitle = remember(formattedBalance, account.asset.symbol, formattedFiat) {
        buildString {
            append(formattedBalance)
            append(" ")
            append(account.asset.symbol)
            if (formattedFiat != null) {
                append(" · ")
                append(formattedFiat)
            }
        }
    }

    AccountCell(
        asset = account.asset,
        title = {
            AccountTitle(asset = account.asset)
        },
        subtitle = {
            if (!showVerification || account.asset.verification.isVerified) {
                MoonItemSubtitle(text = subtitle)
            } else {
                VerificationSubtitle(account.asset.verification)
            }
        },
        content = content,
        position = position,
        onClick = onClick,
    )
}

@Composable
fun SearchAssetCell(
    item: AssetWithDetails,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: () -> Unit = {},
) {
    val formattedPrice = remember(item.asset, item.rate) {
        item.rate?.let { rate ->
            runCatching {
                Formatter.formatFiat(value = item.asset.one, rate = rate.value)
            }.getOrNull()
        }
    }

    val priceDiff = remember(item.rate) {
        item.rate?.percentChange24h
    }

    val formattedMarketCap = remember(item.asset, item.marketCap) {
        item.marketCap?.let { marketCap ->
            runCatching {
                Formatter.formatFiat(value = item.asset.one, rate = marketCap.value)
            }.getOrNull()
        }
    }

    val formattedVolume = remember(item.asset, item.volume) {
        item.volume?.let { volume ->
            runCatching {
                Formatter.formatFiat(value = item.asset.one, rate = volume.value)
            }.getOrNull()
        }
    }

    AccountCell(
        asset = item.asset,
        title = {
            AccountRow {
                AccountTitle(asset = item.asset)
                formattedPrice?.let { MoonItemTitle(text = it) }
            }
        },
        subtitle = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (item.asset.verification.isVerified) {
                    if (formattedMarketCap != null) {
                        MoonItemSubtitle(text = "$formattedMarketCap mcap")
                    } else {
                        formattedVolume?.let {
                            MoonItemSubtitle(text = stringResource(Localization.volume_short_pattern, it))
                        }
                    }
                    priceDiff?.let {
                        MoonItemSubtitle(
                            text = it,
                            color = it.percentDiffColor(),
                        )
                    }
                } else {
                    VerificationSubtitle(item.asset.verification)
                }
            }
        },
        position = position,
        onClick = onClick,
    )
}

@Composable
fun AccountCellShimmer(
    position: MoonBundlePosition = MoonBundlePosition.Default,
    balances: Boolean = true,
) {
    val shimmerPhase = 0f
    val backgroundFill = UIKit.colorScheme.background.contentTint
    val highlightColor = UIKit.colorScheme.background.contentAttention
    MoonBundleCell(position = position) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 76.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = Modifier
                    .size(44.dp)
                    .shimmer(
                        shimmerPhase,
                        cornerRadius = 22.dp,
                        backgroundFill = backgroundFill,
                        highlightColor = highlightColor
                    )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MoonItemTitle(
                        text = "             ",
                        modifier = Modifier.shimmer(
                            shimmerPhase,
                            backgroundFill = backgroundFill,
                            highlightColor = highlightColor
                        ),
                    )
                    if (balances) {
                        MoonItemTitle(
                            text = "             ",
                            modifier = Modifier.shimmer(
                                shimmerPhase,
                                backgroundFill = backgroundFill,
                                highlightColor = highlightColor
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MoonItemSubtitle(
                        text = "                    ",
                        modifier = Modifier.shimmer(
                            shimmerPhase,
                            backgroundFill = backgroundFill,
                            highlightColor = highlightColor
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun AssetImage(
    asset: AssetEntity,
    size: Dp = 44.dp,
    badgeSize: Dp = 20.dp,
) {
    val imageUrl = asset.assetImageUrl()
    val chainImageUrl = asset.tokenChainImageUrl()

    MoonCutBadgedBox(
        badge = if (chainImageUrl != null) {
            { MoonItemImage(image = chainImageUrl, size = badgeSize) }
        } else {
            null
        },
        direction = BadgeDirection.EndBottom,
    ) {
        MoonItemImage(
            image = imageUrl,
            placeholder = painterResource(UIKitIcon.ic_illustration),
            size = size,
        )
    }
}
