package ui.components.moon.cell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun MoonTextContentCell(
    modifier: Modifier = Modifier,
    title: String,
    description: CharSequence?,
    titleStyle: TextStyle = UIKit.typography.h3,
    descriptionStyle: TextStyle = UIKit.typography.body1,
    descriptionColor: Color = Color.Unspecified,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = titleStyle,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center
        )

        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimens.offsetExtraSmall))

            val color = descriptionColor.takeOrElse { UIKit.colorScheme.text.secondary }

            when (description) {
                is AnnotatedString -> Text(
                    text = description,
                    style = descriptionStyle,
                    color = color,
                    textAlign = TextAlign.Center
                )
                else -> Text(
                    text = description.toString(),
                    style = descriptionStyle,
                    color = color,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


