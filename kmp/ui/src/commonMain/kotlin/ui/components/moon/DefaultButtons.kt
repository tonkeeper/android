package ui.components.moon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MoonAccentButton(
    modifier: Modifier = Modifier,
    text: String,
    size: ButtonSize = ButtonSizeMedium,
    buttonColors: ButtonColors = ButtonColorsPrimary,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
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
        icon?.invoke()

        if (icon != null) {
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = text,
            style = size.textStyle
        )
    }
}

@Composable
fun MoonTextButton(
    modifier: Modifier = Modifier,
    text: String,
    size: ButtonSize = ButtonSizeMedium,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(size.height),
        shape = size.shape
    ) {
        Text(
            text = text,
            style = size.textStyle
        )
    }
}

