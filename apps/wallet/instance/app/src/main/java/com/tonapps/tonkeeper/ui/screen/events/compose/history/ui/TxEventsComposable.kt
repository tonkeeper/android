package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxComposableCommand
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsViewModel
import com.tonapps.tonkeeper.ui.screen.events.compose.history.paging.screenState
import com.tonapps.tonkeeper.ui.screen.events.compose.history.state.TxScreenState
import com.tonapps.tonkeeper.ui.screen.events.compose.history.ui.placeholder.TxEventsPlaceholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import ui.animation.ContentCrossfade

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxEventComposable(viewModel: TxEventsViewModel) {

    val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
    val items = viewModel.uiItemsFlow.collectAsLazyPagingItems(coroutineScope.coroutineContext)
    val selectedFilterId by viewModel.selectedFilterIdFlow.collectAsStateWithLifecycle()
    val hiddenBalances by viewModel.hiddenBalancesFlow.collectAsStateWithLifecycle()

    val screenState by remember {
        derivedStateOf {
            items.screenState(viewModel.selectedFilterId)
        }
    }

    LaunchedEffect(items) {
        viewModel.uiCommandFlow
            .filterIsInstance<TxComposableCommand.Refresh>()
            .collect { items.refresh() }
    }

    ContentCrossfade(
        targetState = screenState,
        label = "TxEventsScreenState",
    ) { screenState ->
        when (screenState) {
            TxScreenState.Empty -> TxHistoryEmpty(viewModel)
            TxScreenState.Placeholder -> TxEventsPlaceholder()
            TxScreenState.Error -> TxEventsError(items)
            TxScreenState.List -> TxEventsContent(
                items = items,
                selectedFilterId = selectedFilterId,
                hiddenBalances = hiddenBalances,
                dispatch = viewModel::dispatch,
                uiCommands = viewModel.uiCommandFlow
            )
        }
    }
}