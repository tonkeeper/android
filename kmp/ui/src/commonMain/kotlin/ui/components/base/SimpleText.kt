package ui.components.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SimpleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val layout = remember(text, style) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }

    val width = with(density) { layout.size.width.toDp() }
    val height = with(density) { layout.size.height.toDp() }

    Canvas(modifier.size(width, height)) {
        if (color.isSpecified) {
            drawText(layout, color = color)
        } else {
            drawText(layout)
        }
    }
}