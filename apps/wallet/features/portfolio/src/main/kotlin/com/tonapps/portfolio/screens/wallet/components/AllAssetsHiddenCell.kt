package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.RStr
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.painterResource
import ui.theme.UIKit

@Composable
internal fun AllAssetsHiddenCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HiddenItemsInfoCell(
        title = stringResource(RStr.all_assets_hidden_title),
        subtitle = stringResource(RStr.all_assets_hidden_subtitle),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun AllCollectiblesHiddenCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HiddenItemsInfoCell(
        title = stringResource(RStr.all_collectibles_hidden_title),
        subtitle = stringResource(RStr.all_collectibles_hidden_subtitle),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun HiddenItemsInfoCell(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoonBundleCell(
        modifier = modifier,
        position = MoonBundlePosition.Default,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.background.contentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UIKitIcon.ic_eye_closed_outline_28),
                    tint = UIKit.colorScheme.icon.secondary,
                    contentDescription = null,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                MoonItemTitle(text = title)
                MoonItemSubtitle(text = subtitle)
            }
        }
    }
}
