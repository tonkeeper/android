package ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import ui.theme.UIKit
import ui.theme.modifiers.actionButton

@Composable
fun ActionButtonIcon(
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