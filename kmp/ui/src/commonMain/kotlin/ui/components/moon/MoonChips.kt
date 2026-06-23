package ui.components.moon

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit

@Immutable
data class MoonItem(
    val id: Int,
    val title: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = UIKit.colorScheme

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) colors.buttonPrimary.primaryBackground else colors.buttonSecondary.primaryBackground,
        label = "bg"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) colors.buttonPrimary.primaryForeground else colors.buttonSecondary.primaryForeground,
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

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = UIKit.typography.label2
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonChipBar(
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
