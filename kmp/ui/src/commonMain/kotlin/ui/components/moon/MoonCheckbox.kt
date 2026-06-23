package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.ic_done_bold_16

@Composable
fun MoonCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true
) {
    val size = 22.dp
    val strokeWidth = 2.dp

    val radius = 6.dp

    val backgroundColor = if (checked) {
        UIKit.colorScheme.buttonPrimary.primaryBackground
    } else {
        Color.Transparent
    }

    val borderColor = UIKit.colorScheme.icon.tertiary
    val alpha = if (enabled) 1f else 0.48f

    Box(
        modifier = modifier
            .padding(3.dp)
            .run {
                if (onCheckedChange != null) {
                    clickable(enabled = enabled) {
                        onCheckedChange(!checked)
                    }
                } else {
                    this
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .alpha(alpha)
                .clip(RoundedCornerShape(radius))
                .background(backgroundColor)
                .border(
                    width = if (checked) 0.dp else strokeWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(radius)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_done_bold_16),
                    contentDescription = null,
                    tint = UIKit.colorScheme.buttonPrimary.primaryForeground,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
