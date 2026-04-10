package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsAction
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ui.components.events.UiEvent
import ui.components.moon.MoonRefresh
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell
import ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@NonRestartableComposable
fun TxEventsItems(
    modifier: Modifier,
    items: LazyPagingItems<UiEvent>,
    scope: CoroutineScope,
    listState: LazyListState,
    hiddenBalances: Boolean,
    dispatch: (action: TxEventsAction) -> Unit
) {
    val refreshState = rememberPullToRefreshState()
    val loadState = items.loadState

    SideEffect {
        if (loadState.refresh !is LoadState.Loading && refreshState.distanceFraction == 1f) {
            scope.launch {
                refreshState.animateToHidden()
            }
        }
    }

    PullToRefreshBox(
        modifier = modifier
            .fillMaxWidth(),
        isRefreshing = loadState.refresh is LoadState.Loading,
        onRefresh = { items.refresh() },
        state = refreshState,
        indicator = {
            MoonRefresh(
                modifier = Modifier.align(Alignment.TopCenter),
                state = refreshState
            )
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = Dimens.offsetMedium),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            overscrollEffect = null
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.id },
                contentType = items.itemContentType { it.contentType }
            ) { index ->
                val item = items[index]
                if (item != null) {
                    TxEventItem(
                        item = item,
                        hiddenBalances = hiddenBalances,
                        dispatch = dispatch
                    )
                }
            }
            item(key = "offset", contentType = UiEvent.CONTENT_TYPE_OTHER) {
                when(loadState.append) {
                    is LoadState.Error -> {
                        MoonRetryCell(
                            message = stringResource(Localization.unknown_error),
                            buttonText = stringResource(Localization.retry),
                            onRetry = { items.retry() }
                        )
                    }
                    is LoadState.Loading -> {
                        MoonLoaderCell()
                    }
                    is LoadState.NotLoading ->  { }
                }
            }
        }

    }

}