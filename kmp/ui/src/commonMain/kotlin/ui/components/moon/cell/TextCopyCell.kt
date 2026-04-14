package ui.components.moon.cell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.text.toAnnotatedString

private val DefaultTextCopyCellHeight = 100.dp

@Composable
fun TextCopyCell(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    maxLinesSubtitle: Int = 2,
    image: @Composable (() -> Unit)? = null,
    minHeight: Dp = DefaultTextCopyCellHeight,
    onClick: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    // TODO show toast
    TextCell(
        title = { MoonItemSubtitle(text = title) },
        subtitle = { MoonItemTitle(text = subtitle, maxLines = maxLinesSubtitle) },
        image = image,
        modifier = modifier,
        content = {
            MoonItemIcon(
                size = 24.dp,
                painter = painterResource(UIKitIcon.ic_copy_16),
                color = MaterialTheme.colorScheme.primary,
                onClick = {
                    if (onClick == null) {
                        clipboard.setText(subtitle.toAnnotatedString())
                    } else {
                        onClick()
                    }
                }
            )
        },
        minHeight = minHeight,
        onClick = null,
    )
}
