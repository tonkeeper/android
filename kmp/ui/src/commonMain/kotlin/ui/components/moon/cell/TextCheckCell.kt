package ui.components.moon.cell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import ui.components.moon.MoonItemIcon
import ui.theme.resources.Res
import ui.theme.resources.ic_done_bold_16

@Composable
fun TextCheckCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onBackground,
    isChecked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    maxLinesSubtitle: Int = 1,
    image: @Composable (() -> Unit)? = null,
) {
    TextCell(
        title = title,
        modifier = modifier,
        image = image,
        subtitle = subtitle,
        maxLinesSubtitle = maxLinesSubtitle,
        subtitleColor = subtitleColor,
        content = {
            if (isChecked) {
                MoonItemIcon(
                    painter = painterResource(Res.drawable.ic_done_bold_16),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        onClick = {
            onCheckedChange?.invoke(!isChecked)
        },
    )
}
