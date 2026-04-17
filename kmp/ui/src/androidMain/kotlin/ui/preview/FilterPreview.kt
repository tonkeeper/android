package ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import ui.components.moon.MoonChip
import ui.components.moon.MoonChipBar
import ui.components.moon.MoonItem

@Preview
@Composable
fun MoonChipPreview() {
    ThemedPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoonChip(text = "All", selected = true, onClick = {})
            MoonChip(text = "Sent", selected = false, onClick = {})
        }
    }
}

@Preview
@Composable
fun MoonChipBarPreview() {
    ThemedPreview {
        MoonChipBar(
            filters = persistentListOf(
                MoonItem(id = 0, title = "All"),
                MoonItem(id = 1, title = "Sent"),
                MoonItem(id = 2, title = "Received"),
                MoonItem(id = 3, title = "NFT"),
            ),
            selectedId = 0,
            onSelect = {}
        )
    }
}
