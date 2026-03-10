package ui.components.button

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.theme.ButtonColorsPrimary
import ui.theme.ButtonSize
import ui.theme.ButtonSizeMedium

@Composable
fun TKButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    size: ButtonSize = ButtonSizeMedium,
    buttonColors: ButtonColors = ButtonColorsPrimary,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(size.height),
        shape = size.shape,
        colors = buttonColors,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(
            text = text,
            style = size.textStyle
        )
    }
}
