package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

data class MoonLabelColors(
    val textColor: Color,
    val backgroundColor: Color = textColor.copy(alpha = 0.16f),
)

object MoonLabelDefault {
    @Composable
    @ReadOnlyComposable
    fun grey() : MoonLabelColors {
        return MoonLabelColors(
            textColor = UIKit.colorScheme.text.secondary,
            backgroundColor = UIKit.colorScheme.background.contentTint
        )
    }

    @Composable
    @ReadOnlyComposable
    fun blue() : MoonLabelColors {
        return MoonLabelColors(
            textColor = UIKit.colorScheme.accent.blue,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun error() : MoonLabelColors {
        return MoonLabelColors(
            textColor = UIKit.colorScheme.accent.red,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun orange() : MoonLabelColors {
        return MoonLabelColors(
            textColor = UIKit.colorScheme.accent.orange,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun success() : MoonLabelColors {
        return MoonLabelColors(
            textColor = UIKit.colorScheme.accent.green,
        )
    }
}

@Composable
fun MoonLabel(
    text: String,
    modifier: Modifier = Modifier,
    colors: MoonLabelColors = MoonLabelDefault.grey(),
) {
    Text(
        text = text.uppercase(),
        modifier = modifier
            .height(20.dp)
            .background(
                color = colors.backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(start = 5.dp, top = 4.dp, end = 5.dp, bottom = 5.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = colors.textColor,
        style = UIKit.typography.body4CAPS
    )
}