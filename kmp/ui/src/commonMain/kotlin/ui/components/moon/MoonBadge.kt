package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoonBadge(
    text: String,
    modifier: Modifier = Modifier,
    bgColor: Color = Color.Red,
    textColor: Color = Color.White,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 14.sp)
) {
    Text(
        text = text,
        modifier = modifier
            .background(bgColor, shape = CircleShape)
            .padding(bottom = 1.dp)
            .badgeLayout(),
        style = style,
        color = textColor,
    )
}

private fun Modifier.badgeLayout() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)

    val minPadding = placeable.height / 4

    val width = maxOf(placeable.width + minPadding, placeable.height)
    layout(width, placeable.height) {
        placeable.place((width - placeable.width) / 2, 0)
    }
}
