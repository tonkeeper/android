package com.tonapps.perps.screens.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.screens.markets.PerpsSortSelector
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemTitle
import ui.preview.ThemedPreview

@Composable
internal fun PerpsExploreTitle(
    sort: PerpsSort,
    onSortChange: (PerpsSort) -> Unit,
    onOpenExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 16.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onOpenExplore,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonItemTitle(
                text = stringResource(Localization.perps_explore),
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(2.dp))
            MoonChevronRight()
        }
        PerpsSortSelector(
            sort = sort,
            onSortChange = onSortChange,
        )
    }
}

@Preview
@Composable
private fun PerpsExploreTitlePreview() {
    ThemedPreview {
        PerpsExploreTitle(
            sort = PerpsSort.OPEN_INTEREST,
            onSortChange = {},
            onOpenExplore = {},
        )
    }
}
