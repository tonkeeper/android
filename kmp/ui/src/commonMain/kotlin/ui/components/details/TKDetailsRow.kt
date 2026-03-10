package ui.components.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.ComposeIcon
import ui.theme.UIKit

@Composable
fun TKDetailsRow(
    modifier: Modifier = Modifier,
    key: CharSequence,
    value: CharSequence,
    iconLeft: ComposeIcon?,
    secondaryValue: CharSequence?,
    spoiler: Boolean
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TKDetailsLine(
            key = key,
            value = value,
            iconLeft = iconLeft,
            spoiler = spoiler
        )

        secondaryValue?.let {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = it.toString(),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.End
            )
        }
    }
}