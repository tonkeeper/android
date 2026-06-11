package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxComposableCommand
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsViewModel
import com.tonapps.tonkeeper.ui.screen.events.compose.history.paging.screenState
import com.tonapps.tonkeeper.ui.screen.events.compose.history.state.TxScreenState
import com.tonapps.tonkeeper.ui.screen.events.compose.history.ui.placeholder.TxEventsPlaceholder
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import ui.animation.ContentCrossfade
import ui.components.base.UIKitScaffold
import ui.components.moon.MoonTopAppBarLarge
import ui.components.moon.MoonTopAppBarSimple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxEventComposable(
    viewModel: TxEventsViewModel,
    canGoBack: Boolean,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
    val items = viewModel.uiItemsFlow.collectAsLazyPagingItems(coroutineScope.coroutineContext)
    val selectedFilterId by viewModel.selectedFilterIdFlow.collectAsStateWithLifecycle()
    val hiddenBalances by viewModel.hiddenBalancesFlow.collectAsStateWithLifecycle()

    val screenState by remember {
        derivedStateOf { items.screenState(viewModel.selectedFilterId) }
    }

    LaunchedEffect(items) {
        viewModel.uiCommandFlow.filterIsInstance<TxComposableCommand.Refresh>()
            .collect { items.refresh() }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    UIKitScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (canGoBack) {
                MoonTopAppBarSimple(
                    modifier = Modifier.statusBarsPadding(),
                    title = stringResource(Localization.history),
                    navigationIconRes = UIKitIcon.ic_chevron_left_16,
                    onNavigationClick = onBack,
                    scrollBehavior = scrollBehavior,
                )
            } else if (screenState == TxScreenState.List || screenState == TxScreenState.Placeholder) {
                MoonTopAppBarLarge(
                    title = stringResource(Localization.history),
                    scrollBehavior = scrollBehavior,
                )
            }
        }
    ) { innerPadding ->
        ContentCrossfade(
            targetState = screenState,
            label = "TxEventsScreenState",
        ) { state ->
            when (state) {
                TxScreenState.Empty -> TxHistoryEmpty(
                    viewModel = viewModel,
                    innerPadding = innerPadding,
                )
                TxScreenState.Placeholder -> TxEventsPlaceholder(
                    innerPadding = innerPadding,
                )
                TxScreenState.Error -> TxEventsError(
                    items = items,
                    innerPadding = innerPadding,
                )
                TxScreenState.List -> TxEventsContent(
                    innerPadding = innerPadding,
                    canGoBack = canGoBack,
                    items = items,
                    selectedFilterId = selectedFilterId,
                    hiddenBalances = hiddenBalances,
                    dispatch = viewModel::dispatch,
                    uiCommands = viewModel.uiCommandFlow,
                    scrollBehavior = scrollBehavior,
                )
            }
        }
    }
}
