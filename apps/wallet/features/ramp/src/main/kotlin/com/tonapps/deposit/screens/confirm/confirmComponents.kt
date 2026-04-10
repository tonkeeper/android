package com.tonapps.deposit.screens.confirm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.theme.UIKit

@Composable
internal fun FeeItemCell(
    image: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        image()

        Column(
            modifier = Modifier.width(200.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MoonItemTitle(title)
            MoonItemSubtitle(subtitle)
        }

        MoonItemIcon(
            modifier = Modifier.alpha(if (isChecked) 1f else 0f),
            painter = painterResource(UIKitIcon.ic_done_16),
            color = UIKit.colorScheme.accent.blue,
        )
    }
}
