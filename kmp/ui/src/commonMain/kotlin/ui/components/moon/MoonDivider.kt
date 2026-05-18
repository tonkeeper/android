package ui.components.moon

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
@NonRestartableComposable
fun MoonItemDivider(
    modifier: Modifier = Modifier,
) {
    MoonDivider(
        modifier = modifier.padding(start = 16.dp)
    )
}

@Composable
@NonRestartableComposable
fun MoonDivider(
    modifier: Modifier = Modifier,
) {
    val color = UIKit.colorScheme.separator.common
    val dividerSize = .5f.dp

    HorizontalDivider(
        color = color,
        thickness = dividerSize,
        modifier = modifier
    )
}

@Composable
@NonRestartableComposable
fun MoonVerticalDivider(
    modifier: Modifier = Modifier,
) {
    val color = UIKit.colorScheme.separator.common
    val dividerSize = .5f.dp

    VerticalDivider(
        modifier = modifier,
        color = color,
        thickness = dividerSize,
    )
}