package ui.components.moon

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
fun MoonLoader(
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(
        modifier = modifier,
        strokeWidth = 2.dp,
        color = UIKit.colorScheme.icon.secondary
    )
}