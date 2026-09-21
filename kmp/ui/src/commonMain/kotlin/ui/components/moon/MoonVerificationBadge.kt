package ui.components.moon

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.ic_verification_16

@Composable
fun MoonVerificationBadge(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = UIKit.colorScheme.accent.blue,
) {
    Icon(
        painter = painterResource(Res.drawable.ic_verification_16),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}
