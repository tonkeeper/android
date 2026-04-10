package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxComposableCommand
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import ui.components.events.UiEvent
import ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxEventsContent(
    innerPadding: PaddingValues,
    canGoBack: Boolean,
    items: LazyPagingItems<UiEvent>,
    selectedFilterId: Int,
    hiddenBalances: Boolean,
    uiCommands: SharedFlow<TxComposableCommand>,
    scrollBehavior: TopAppBarScrollBehavior,
    dispatch: (action: TxEventsAction) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        val listState = rememberLazyListState()

        LaunchedEffect(listState) {
            uiCommands
                .filterIsInstance<TxComposableCommand.ScrollUp>()
                .collect {
                    delay(32)
                    listState.scrollToItem(0)
                    with(scrollBehavior.state) {
                        heightOffset = 0f
                        contentOffset = 0f
                    }
                }
        }

        TxFiltersBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.heightItem),
            listState = listState,
            selectedId = selectedFilterId,
            dispatch = dispatch
        )

        val bottomPadding = if (canGoBack) 0.dp else Dimens.heightBar

        TxEventsItems(
            modifier = Modifier.padding(top = Dimens.heightItem, bottom = bottomPadding),
            items = items,
            scope = coroutineScope,
            listState = listState,
            hiddenBalances = hiddenBalances,
            dispatch = dispatch
        )
    }
}
