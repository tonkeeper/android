package ui.components.moon.cell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLoader
import ui.theme.UIKit

@Composable
fun MoonErrorCell(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.accent.red,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}
