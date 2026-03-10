package ui.components.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersBar(
    modifier: Modifier = Modifier,
    filters: ImmutableList<UiFilter>,
    selectedId: Int,
    onSelect: (UiFilter) -> Unit,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Dimens.offsetMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.offsetMedium / 2)
    ) {
        items(
            count = filters.size,
            key = { filters[it].id },
            contentType = { "chip" }
        ) { index ->
            val filter = filters[index]
            FilterChip(
                text = filter.title,
                selected = filter.id == selectedId,
                onClick = { onSelect(filter) }
            )
        }
    }
}