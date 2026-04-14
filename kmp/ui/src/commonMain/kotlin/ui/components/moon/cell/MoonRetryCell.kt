package ui.components.moon.cell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonAccentButton
import ui.components.moon.ButtonSizeSmall
import ui.theme.UIKit

@Composable
fun MoonRetryCell(
    message: String,
    buttonText: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary
            )
            MoonAccentButton(
                text = buttonText,
                onClick = onRetry,
                size = ButtonSizeSmall
            )
        }
    }
}