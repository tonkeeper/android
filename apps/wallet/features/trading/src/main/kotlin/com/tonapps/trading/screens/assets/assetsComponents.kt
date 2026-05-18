package com.tonapps.trading.screens.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonapps.trading.percentDiffColor
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell

@Composable
internal fun AssetCell(
    item: AssetItem,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: () -> Unit = {},
) {
    MoonBundleCell(position = position) {
        TextCell(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemTitle(text = item.symbol)
                    MoonItemTitle(text = item.formattedPrice)
                }
            },
            subtitle = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemSubtitle(text = item.name)
                    MoonItemSubtitle(
                        text = item.formattedChange,
                        color = item.formattedChange.percentDiffColor(),
                    )
                }
            },
            image = {
                MoonItemImage(image = item.imageUrl, size = 44.dp)
            },
            onClick = onClick,
            minHeight = 76.dp,
        )
    }
}
