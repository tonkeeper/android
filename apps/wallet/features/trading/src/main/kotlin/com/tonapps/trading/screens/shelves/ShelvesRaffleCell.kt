package com.tonapps.trading.screens.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.components.moon.MoonItemIcon
import ui.components.moon.cell.DefaultItemHeight
import ui.components.moon.cell.MoonBundleCell
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
internal fun ShelvesRaffleCell(
    item: ShelvesRaffleCellItem,
    onClick: () -> Unit,
) {
    MoonBundleCell(
        margins = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DefaultItemHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonItemIcon(
                painter = painterResource(item.iconRes),
                color = UIKit.colorScheme.accent.blue,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.title,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            item.points?.let { points ->
                Text(
                    text = if (points.wholeCount == null) {
                        stringResource(Localization.raffle_points_fractional, points.formatted)
                    } else {
                        pluralStringResource(Plurals.raffle_points, points.wholeCount, points.formatted)
                    },
                    style = UIKit.typography.label2,
                    color = UIKit.colorScheme.accent.blue,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(UIKit.colorScheme.accent.blue.copy(alpha = 0.12f))
                        .heightIn(min = 28.dp)
                        .wrapContentHeight()
                        .padding(horizontal = 12.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_chevron_right_16),
                size = 16.dp,
            )
        }
    }
}

@Preview
@Composable
private fun ShelvesRaffleCellNotJoinedPreview() {
    ThemedPreview(isDarkOnly = true) {
        ShelvesRaffleCell(
            item = ShelvesRaffleCellItem(
                raffleId = "dinner_with_trump",
                title = "Win a private dinner with Trump",
                iconRes = UIKitIcon.ic_dinner_28,
                points = null,
            ),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun ShelvesRaffleCellJoinedPreview() {
    ThemedPreview(isDarkOnly = true) {
        ShelvesRaffleCell(
            item = ShelvesRaffleCellItem(
                raffleId = "dinner_with_trump",
                title = "Dinner with Trump",
                iconRes = UIKitIcon.ic_dinner_28,
                points = ShelvesRaffleCellItem.Points(formatted = "1 234", wholeCount = 1234),
            ),
            onClick = {},
        )
    }
}
