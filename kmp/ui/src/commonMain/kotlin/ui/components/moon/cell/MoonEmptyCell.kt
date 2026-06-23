package ui.components.moon.cell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.MoonAccentButton
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun MoonEmptyCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    firstButtonText: String? = null,
    onFirstClick: () -> Unit = {},
    secondButtonText: String? = null,
    onSecondClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.offsetExtraSmall)
            )
        }

        if (firstButtonText != null || secondButtonText != null) {
            Row(
                modifier = Modifier.padding(top = Dimens.offsetMedium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (firstButtonText != null) {
                    MoonAccentButton(
                        text = firstButtonText,
                        onClick = onFirstClick,
                        buttonColors = ButtonColorsSecondary,
                    )
                }

                if (secondButtonText != null) {
                    MoonAccentButton(
                        modifier = Modifier.padding(start = Dimens.offsetMedium),
                        text = secondButtonText,
                        onClick = onSecondClick,
                        buttonColors = ButtonColorsSecondary,
                    )
                }
            }
        }
    }
}