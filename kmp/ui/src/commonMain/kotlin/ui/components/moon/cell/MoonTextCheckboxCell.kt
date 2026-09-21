package ui.components.moon.cell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonCheckbox
import ui.components.moon.MoonItemIcon
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun MoonTextCheckboxCell(
    text: String,
    isChecked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    onInfo: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = remember { PaddingValues(horizontal = 16.dp, vertical = 12.dp) }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                onCheckedChanged(!isChecked)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        MoonCheckbox(checked = isChecked, enabled = enabled)

        Text(
            text = text,
            style = UIKit.typography.body1,
            color = if (enabled) {
                UIKit.colorScheme.text.secondary
            } else {
                UIKit.colorScheme.text.tertiary
            },
        )

        if (onInfo != null) {
            MoonItemIcon(painterResource(UIKitIcon.ic_information_circle_16))
        }
    }
}



@Preview
@Composable
fun MoonCheckboxPreview() {
    ThemedPreview {
        MoonTextCheckboxCell("Some description", false, {}, {})
    }
}

