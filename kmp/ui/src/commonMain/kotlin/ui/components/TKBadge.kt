package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
fun TKBadge(
    modifier: Modifier = Modifier,
    text: String,
    textColor: Color = UIKit.colorScheme.text.secondary,
    backgroundColor: Color = UIKit.colorScheme.background.contentTint
) {
    Text(
        text = text.uppercase(),
        modifier = modifier
            .height(20.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(start = 5.dp, top = 4.dp, end = 5.dp, bottom = 5.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = textColor,
        style = UIKit.typography.body4CAPS
    )
}