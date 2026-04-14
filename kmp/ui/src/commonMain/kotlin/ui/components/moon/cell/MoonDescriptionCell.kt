package ui.components.moon.cell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
fun MoonDescriptionCell(
    text: CharSequence,
    modifier: Modifier = Modifier,
) {
    when (text) {
        is AnnotatedString -> Text(
            text = text,
            modifier = modifier.fillMaxWidth()
                .padding(16.dp),
            color = UIKit.colorScheme.text.secondary,
            style = UIKit.typography.body2,
        )

        else -> Text(
            text = text.toString(),
            modifier = modifier.fillMaxWidth()
                .padding(16.dp),
            color = UIKit.colorScheme.text.secondary,
            style = UIKit.typography.body2,
        )
    }
}
