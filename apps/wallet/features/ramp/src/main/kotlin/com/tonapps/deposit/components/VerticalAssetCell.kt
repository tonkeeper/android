package com.tonapps.deposit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.contract.TokenType
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelColors
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.painterResource
import ui.theme.UIKit

@Composable
fun VerticalAssetCell(
    currency: WalletCurrency,
    isAbstract: Boolean = false,
    isSelected: Boolean = false,
    content: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    VerticalAssetCell(
        name = currency.code,
        extendedName = if (isAbstract) null else currency.title,
        standard = if (isAbstract) null else currency.tokenType?.fmt,
        tagColor = currency.tokenType?.color(),
        assetImageUrl = currency.iconUri?.toString(),
//        chainImageUrl = remember {
//            when (currency.tokenType != null) {
//                true -> currency.chain.iconExternalUrl(context)
//                false -> null
//            }
//        },
        content = content ?: {
            if (isSelected) {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_donemark_thin_28),
                    color = UIKit.colorScheme.accent.blue,
                )
            }
        },
        titleContent = titleContent,
        onClick = onClick,
    )
}

@Composable
@ReadOnlyComposable
fun TokenType.color(): MoonLabelColors {
    return when (this) {
        TokenType.Defined.JETTON -> MoonLabelDefault.blue()
        TokenType.Defined.TRC20 -> MoonLabelDefault.error()
        TokenType.Defined.BEP20 -> MoonLabelDefault.orange()
        else -> MoonLabelDefault.grey()
    }
}

@Composable
fun VerticalAssetCell(
    name: String,
    assetImageUrl: String?,
    extendedName: String? = null,
    chainImageUrl: String? = null,
    standard: String? = null,
    content: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    tagColor: MoonLabelColors? = null,
    onClick: (() -> Unit)? = null,
) {
    TextCell(
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoonItemTitle(text = name)

                if (standard != null) {
                    val color = tagColor ?: MoonLabelDefault.grey()
                    MoonLabel(standard, colors = color)
                }

                if (extendedName != null) {
                    MoonLargeItemSubtitle(text = extendedName)
                }

                titleContent?.invoke()
            }
        },
        image = {
            if (chainImageUrl != null) {
                MoonCutBadgedBox(
                    badge = { MoonItemImage(image = chainImageUrl, size = 14.dp) },
                    direction = BadgeDirection.EndBottom,
                ) {
                    MoonItemImage(image = chainImageUrl, size = 28.dp)
                }
            } else {
                MoonItemImage(image = assetImageUrl, size = 28.dp)
            }
        },
        content = content,
        onClick = onClick,
        minHeight = 52.dp,
    )
}



@Composable
fun VerticalItemCell(
    title: String,
    image: @Composable () -> Unit,
    tags: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
    onClick: () -> Unit = {},
) {
    TextCell(
        title = title,
        image = image,
        tags = tags,
        content = content,
        onClick = onClick,
        minHeight = 52.dp,
    )
}