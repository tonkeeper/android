package uikit.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import uikit.compose.AppTheme
import uikit.compose.UIKit

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val size = 22.dp
    val strokeWidth = 2.dp
    val radius = 6.dp

    val backgroundColor = if (checked) {
        UIKit.colors.buttonPrimaryBackground
    } else {
        Color.Transparent
    }

    val borderColor = UIKit.colors.iconTertiary
    val alpha = if (enabled) 1f else 0.48f

    Box(modifier = modifier
        .padding(3.dp)
        .clickable(enabled = enabled) {
            onCheckedChange(!checked)
        }) {
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
                val icon = UIKitIcon.ic_done_bold_16
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = UIKit.colors.buttonPrimaryForeground,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(name = "CheckBox - Unchecked", showBackground = true)
@Composable
fun CheckBoxUncheckedPreview() {
    var checked by remember { mutableStateOf(false) }

    UIKit(theme = AppTheme.BLUE) {
        Checkbox(
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }
}

@Preview(name = "CheckBox - Checked", showBackground = true)
@Composable
fun CheckBoxCheckedPreview() {
    var checked by remember { mutableStateOf(true) }

    UIKit(theme = AppTheme.BLUE) {
        Checkbox(
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }
}
