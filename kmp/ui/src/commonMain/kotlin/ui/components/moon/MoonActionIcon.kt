package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import ui.theme.Dimens
import ui.theme.UIKit

@Deprecated("Use MoonActionIcon instead")
@Composable
fun MoonActionIcon(
    painter: Painter,
    contentDescription: String? = null,
    onClick: () -> Unit
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .actionButton(),
        tint = UIKit.colorScheme.buttonSecondary.primaryForeground
    )
}

@Composable
private fun Modifier.actionButton() = size(Dimens.sizeAction)
    .background(
        color = UIKit.colorScheme.buttonSecondary.primaryBackground,
        shape = CircleShape
    )
    .padding(8.dp)