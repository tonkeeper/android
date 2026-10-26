package com.tonapps.portfolio.screens.wallet.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonRichItemImage
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.painterResource
import ui.theme.UIKit

private val AssetIconSize = 44.dp
private val NetworkIconSize = 20.dp

@Composable
internal fun EarnAssetIcon(
    assetSymbol: String,
    assetIconUrl: String?,
    @DrawableRes networkIcon: Int?,
) {
    MoonCutBadgedBox(
        direction = BadgeDirection.EndBottom,
        badge = if (networkIcon != null) {
            {
                MoonItemImage(
                    modifier = Modifier.background(
                        color = UIKit.colorScheme.background.contentTint,
                        shape = CircleShape,
                    ),
                    painter = painterResource(networkIcon),
                    size = NetworkIconSize,
                )
            }
        } else {
            null
        },
    ) {
        MoonRichItemImage(
            image = assetIconUrl,
            preview = assetSymbol,
            size = AssetIconSize,
        )
    }
}