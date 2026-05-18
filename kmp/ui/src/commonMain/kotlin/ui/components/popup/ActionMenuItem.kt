package ui.components.popup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.ComposeIcon
import ui.components.moon.MoonAsyncImage
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun ActionMenuItem(
    text: String,
    painter: Painter?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = Dimens.offsetMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = UIKit.typography.label1,
            color = UIKit.colorScheme.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = UIKit.colorScheme.accent.blue
            )
        }
    }
}

@Composable
fun ActionMenuItem(
    text: String,
    icon: ComposeIcon?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = Dimens.offsetMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = UIKit.typography.label1,
            color = UIKit.colorScheme.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        icon?.let {
            MoonAsyncImage(
                size = 16.dp,
                image = it.url,
                colorFilter = ColorFilter.tint(UIKit.colorScheme.accent.blue)
            )
        }
    }
}