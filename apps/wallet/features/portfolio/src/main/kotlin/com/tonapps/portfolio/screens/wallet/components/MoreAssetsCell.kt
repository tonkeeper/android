package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.core.components.assetImageUrl
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.localization.RStr
import ui.components.moon.MoonItemImage
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.painterResource
import ui.theme.UIKit

private val IconSize = 24.dp
private val IconRing = 2.dp
private val IconOverlap = 8.dp
private const val MaxPreviewIcons = 2

@Composable
internal fun MoreAssetsCell(
    assets: List<AssetEntity>,
    position: MoonBundlePosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoonBundleCell(
        modifier = modifier,
        position = position,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (assets.isNotEmpty()) {
                MoreAssetsIcons(
                    assets = assets,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Text(
                text = stringResource(RStr.more_assets),
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
            )
            Icon(
                modifier = Modifier.padding(start = 4.dp),
                painter = painterResource(UIKitIcon.ic_chevron_down_16),
                tint = UIKit.colorScheme.icon.secondary,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun MoreAssetsIcons(
    assets: List<AssetEntity>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(-IconOverlap),
    ) {
        assets.take(MaxPreviewIcons).forEach { asset ->
            Box(
                modifier = Modifier
                    .size(IconSize + IconRing * 2)
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.background.content),
                contentAlignment = Alignment.Center,
            ) {
                MoonItemImage(
                    image = asset.assetImageUrl(),
                    size = IconSize,
                )
            }
        }
    }
}
