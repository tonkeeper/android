package ui.components.moon.cell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.components.moon.MoonItemIcon
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.ic_exclamationmark_triangle_16

@Composable
fun MoonInfoCell(
    text: CharSequence,
    modifier: Modifier = Modifier,
    painter: Painter = painterResource(Res.drawable.ic_exclamationmark_triangle_16),
    color: Color = UIKit.colorScheme.text.primary,
    textStyle: TextStyle = UIKit.typography.body2,
    maxLines: Int = 3
) {
   MoonBundleCell {
       Row(
           modifier.fillMaxWidth()
               .padding(vertical = 12.dp, horizontal = 16.dp),
           horizontalArrangement = Arrangement.spacedBy(16.dp)
       ) {
           when (text) {
               is AnnotatedString -> Text(
                   modifier = Modifier.weight(1f),
                   text = text,
                   color = color,
                   maxLines = maxLines,
                   style = textStyle,
               )

               else -> Text(
               modifier = Modifier.weight(1f),
                   text = text.toString(),
                   color = color,
                   maxLines = maxLines,
                   style = textStyle,
               )
           }

           MoonItemIcon(
               painter = painter,
               color = UIKit.colorScheme.icon.secondary
           )
       }
   }
}