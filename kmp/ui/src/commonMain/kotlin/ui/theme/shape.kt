package ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

// TODO to design system
@Composable
@ReadOnlyComposable
fun outlineStoke(): BorderStroke {
    return BorderStroke(
        width = 0.5.dp,
        color = UIKit.colorScheme.separator.common
    )
}
