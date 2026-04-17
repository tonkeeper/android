package ui.components.moon.cell

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.painterResource
import ui.theme.UIKit

@Composable
fun TextCheckCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = UIKit.colorScheme.text.secondary,
    isChecked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    maxLinesSubtitle: Int = 1,
    paddingBetween: Boolean = true,
    tags: (@Composable RowScope.() -> Unit)? = null,
    description: (@Composable () -> Unit)? = null,
    image: @Composable (() -> Unit)? = null,
    minHeight: Dp = DefaultItemHeight,
) {
    TextCell(
        title = title,
        modifier = modifier,
        image = image,
        tags = tags,
        description = description,
        subtitle = subtitle,
        paddingBetween = paddingBetween,
        maxLinesSubtitle = maxLinesSubtitle,
        subtitleColor = subtitleColor,
        content = {
            if (isChecked) {
                MoonItemIcon(
                    size = 28.dp,
                    painter = painterResource(UIKitIcon.ic_donemark_thin_28),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        minHeight = minHeight,
        onClick = if (onCheckedChange != null) {
            { onCheckedChange(!isChecked) }
        } else {
            null
        },
    )
}
