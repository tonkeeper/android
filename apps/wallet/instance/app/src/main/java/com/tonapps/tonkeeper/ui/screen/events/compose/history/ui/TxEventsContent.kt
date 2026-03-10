package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.LazyPagingItems
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxComposableCommand
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsAction
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import ui.components.bar.top.LargeTopAppBar
import ui.components.base.UIKitScaffold
import ui.components.events.UiEvent
import ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxEventsContent(
    items: LazyPagingItems<UiEvent>,
    selectedFilterId: Int,
    hiddenBalances: Boolean,
    uiCommands: SharedFlow<TxComposableCommand>,
    dispatch: (action: TxEventsAction) -> Unit
) {

    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    UIKitScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = stringResource(Localization.history),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
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

            TxEventsItems(
                items = items,
                scope = coroutineScope,
                listState = listState,
                hiddenBalances = hiddenBalances,
                dispatch = dispatch
            )
        }

    }
}
