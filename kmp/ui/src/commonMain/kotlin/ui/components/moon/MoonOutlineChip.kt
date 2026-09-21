package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun MoonOutlineChip(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(UIKit.shapes.large)
            .clickable(onClick = onClick)
            .border(
                width = 2.dp,
                color = UIKit.colorScheme.buttonTertiary.primaryBackground,
                shape = UIKit.shapes.large
            )
            .background(
                when (isSelected) {
                    true -> UIKit.colorScheme.buttonTertiary.primaryBackground
                    false -> UIKit.colorScheme.background.page
                },
            )
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.primary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}


@Preview
@Composable
private fun MoonOutlineChipPreview() {
    ThemedPreview {
        Row {
            MoonOutlineChip("5%", true) {}
            MoonOutlineChip("10%", false) {}
        }
    }
}