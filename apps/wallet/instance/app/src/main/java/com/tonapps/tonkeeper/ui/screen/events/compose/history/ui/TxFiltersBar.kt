package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsAction
import com.tonapps.tonkeeper.ui.screen.events.compose.history.state.supportedTxFilters
import com.tonapps.tonkeeper.ui.screen.events.compose.history.state.toUi
import kotlinx.collections.immutable.toImmutableList
import ui.components.filter.FiltersBar
import ui.components.filter.UiFilter
import ui.theme.modifiers.bottomDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxFiltersBar(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    selectedId: Int,
    dispatch: (action: TxEventsAction) -> Unit
) {

    val context = LocalContext.current

    val filters = remember {
        supportedTxFilters.map { it.toUi(context) }.toImmutableList()
    }

    val scrolledDown by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    val onSelectClick = { filter: UiFilter ->
        dispatch(TxEventsAction.SelectFilter(filter.id))
    }

    FiltersBar(
        modifier = modifier.bottomDivider(enabled = scrolledDown, insetStart = 0.dp),
        filters = filters,
        selectedId = selectedId,
        onSelect = onSelectClick,
    )
}