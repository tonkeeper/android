package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import ui.components.filter.FilterChip
import ui.components.filter.FiltersBar
import ui.components.filter.UiFilter

@Preview
@Composable
fun FilterChipPreview() {
    ThemedPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(text = "All", selected = true, onClick = {})
            FilterChip(text = "Sent", selected = false, onClick = {})
        }
    }
}

@Preview
@Composable
fun FiltersBarPreview() {
    ThemedPreview {
        FiltersBar(
            filters = persistentListOf(
                UiFilter(id = 0, title = "All"),
                UiFilter(id = 1, title = "Sent"),
                UiFilter(id = 2, title = "Received"),
                UiFilter(id = 3, title = "NFT"),
            ),
            selectedId = 0,
            onSelect = {}
        )
    }
}
