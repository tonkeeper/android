package com.tonapps.deposit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.core.extensions.iconExternalUrl
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelColors
import ui.components.moon.MoonLabelDefault
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.painterResource
import ui.theme.UIKit

@Composable
fun AssetCell(
    entity: TokenEntity,
    description: String,
    isSelected: Boolean = false,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val chainIcon = remember(entity.tokenType) {
        entity.tokenType?.let {
             entity.asCurrency.chain.iconExternalUrl(context)
        }
    }

    AssetCell(
        title = entity.symbol,
        position = position,
        subtitle = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoonItemSubtitle(text = description)
            }
        },
        isSelected = isSelected,
        standard = entity.tokenType?.fmt,
        tagColor = entity.tokenType?.color(),
        assetImageUrl = entity.imageUri.toString(),
        chainImageUrl = chainIcon,
        onClick = onClick,
    )
}


@Composable
fun AssetCell(
    title: String,
    subtitle: (@Composable () -> Unit)? = null,
    assetImageUrl: String?,
    chainImageUrl: String? = null,
    standard: String? = null,
    isExpanded: Boolean = false,
    isSelected: Boolean = false,
    tagColor: MoonLabelColors? = null,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    maxLinesTitle: Int = 1,
    onClick: () -> Unit = {},
) {
    MoonBundleCell(position = position) {
        TextCell(
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemTitle(
                        text = title,
                        maxLines = maxLinesTitle,
                    )

                    if (standard != null) {
                        val color = tagColor ?: MoonLabelDefault.grey()
                        MoonLabel(standard, colors = color)
                    }
                }
            },
            subtitle = subtitle,
            image = {
                MoonCutBadgedBox(
                    badge = if (chainImageUrl != null) {
                        { MoonItemImage(image = chainImageUrl, size = 18.dp) }
                    } else {
                        null
                    },
                    direction = BadgeDirection.EndBottom,
                ) {
                    MoonItemImage(image = assetImageUrl, size = 44.dp)
                }
            },
            content = when {
                isExpanded -> {
                    {
                        MoonItemIcon(painter = painterResource(UIKitIcon.ic_chevron_right_16))
                    }
                }

                isSelected -> {
                    {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_donemark_28),
                            color = UIKit.colorScheme.accent.blue,
                        )
                    }
                }

                else -> null
            },
            onClick = onClick,
            minHeight = 76.dp,
        )
    }
}