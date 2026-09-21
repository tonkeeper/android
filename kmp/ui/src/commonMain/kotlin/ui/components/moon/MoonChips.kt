package ui.components.moon

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ui.components.moon.list.rememberScrollIntoViewIfNeeded
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit

@Immutable
data class MoonItem(
    val id: Int,
    val title: String,
    val image: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonChip(
    text: String,
    image: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = UIKit.colorScheme

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) colors.background.contentAttention else colors.buttonSecondary.primaryBackground,
        label = "bg"
    )

    val textColor by animateColorAsState(
        targetValue = colors.buttonSecondary.primaryForeground,
        label = "text"
    )

    val modifier = Modifier
        .clip(Shapes.medium)
        .selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Checkbox,
        )
        .height(Dimens.sizeAction)

    Row(
        modifier = modifier
            .background(backgroundColor)
            .padding(start = if (image != null) 6.dp else 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (image != null) {
            MoonItemImage(
                image = image,
                size = 20.dp
            )
        }

        Text(
            text = text,
            color = textColor,
            style = UIKit.typography.label2
        )
    }
}

val MoonChipBarCellDefaultPadding = PaddingValues(
    start = Dimens.offsetMedium,
    end = Dimens.offsetMedium,
    top = 8.dp,
    bottom = Dimens.offsetMedium,
)

@Composable
fun MoonChipBarCell(
    modifier: Modifier = Modifier,
    filters: ImmutableList<MoonItem>,
    selectedId: Int? = null,
    scrollToSelected: Boolean = false,
    contentPadding: PaddingValues = MoonChipBarCellDefaultPadding,
    onSelect: (MoonItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollIntoViewIfNeeded = rememberScrollIntoViewIfNeeded(listState)

    if (scrollToSelected) {
        LaunchedEffect(selectedId, filters) {
            val selectedIndex = filters.indexOfFirst { it.id == selectedId }
            if (selectedIndex >= 0) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
                scrollIntoViewIfNeeded(selectedIndex, contentPadding)
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            count = filters.size,
            key = { filters[it].id },
            contentType = { "chip" }
        ) { index ->
            val filter = filters[index]
            MoonChip(
                text = filter.title,
                image = filter.image,
                selected = filter.id == selectedId,
                onClick = {
                    onSelect(filter)
                    scope.launch { scrollIntoViewIfNeeded(index, contentPadding) }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Deprecated("Use cell")
@Composable
fun MoonChipBar(
    // TODO remove and migrate to cell
    modifier: Modifier = Modifier,
    filters: ImmutableList<MoonItem>,
    selectedId: Int? = null,
    onSelect: (MoonItem) -> Unit,
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
            MoonChip(
                text = filter.title,
                selected = filter.id == selectedId,
                onClick = { onSelect(filter) }
            )
        }
    }
}

