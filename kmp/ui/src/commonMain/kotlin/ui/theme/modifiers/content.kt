package ui.theme.modifiers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun Modifier.contentTintBackground(
    shape: Shape = RoundedCornerShape(18.dp)
): Modifier = composed {
    background(
        color = UIKit.colorScheme.background.contentTint,
        shape = shape
    )
}

@Composable
fun Modifier.actionButton() = size(Dimens.sizeAction)
    .background(
        color = UIKit.colorScheme.buttonSecondary.primaryBackground,
        shape = CircleShape
    )
    .padding(8.dp)
