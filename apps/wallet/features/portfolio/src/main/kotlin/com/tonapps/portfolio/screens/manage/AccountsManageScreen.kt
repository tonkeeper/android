package com.tonapps.portfolio.screens.manage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.tonapps.core.components.AccountCell
import com.tonapps.core.components.AccountCellShimmer
import com.tonapps.core.components.ChainFilterBar
import com.tonapps.core.components.FilterSwitchItem
import com.tonapps.core.components.FiltersSheet
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.theme.UIKit

private const val EMPTY_KEY = 1
private const val SHIMMER_KEY = 2
private const val LOADING_MORE_KEY = 3
private const val SAVE_BUTTON_ANIM_MS = 200

@Composable
fun AccountsManageScreen(
    feature: AccountsManageFeature,
    onBack: () -> Unit,
) {
    val pagingItems = feature.accountsFlow.collectAsLazyPagingItems()
    val hiddenOverrides by feature.hiddenOverrides.collectAsState()
    val pendingVisibilityChanges by feature.pendingVisibilityChanges.collectAsState()
    val isSavingVisibility by feature.isSavingVisibility.collectAsState()
    val searchText = rememberSaveable { mutableStateOf("") }
    val selectedChain by feature.networkFilter.collectAsState()
    val hideDust by feature.hideDust.collectAsState()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isLoading = pagingItems.loadState.refresh is LoadState.Loading
    val isEmpty = !isLoading && pagingItems.itemCount == 0
    val showSkeleton = isLoading && pagingItems.itemCount == 0

    LaunchedEffect(selectedChain, searchText.value, hideDust) {
        listState.scrollToItem(0)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                keyboardController?.hide()
                focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    val showButton = pendingVisibilityChanges.isNotEmpty()

    MoonScaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection()),
        topBar = {
            MoonTopAppBar(
                title = stringResource(Localization.manage),
                navigationIconRes = UIKitIcon.ic_sliders_16,
                onNavigationClick = { showFilters = true },
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onBack,
                ignoreSystemOffset = true,
                showDivider = false,
                backgroundColor = Color.Transparent,
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            MoonSearchCell(
                searchText = searchText,
                placeholder = stringResource(Localization.search_by_ticker),
                onChanged = {
                    searchText.value = it
                    feature.onSearch(it)
                },
            )

            ChainFilterBar(
                selectedNetwork = selectedChain,
                onNetworkSelected = { network ->
                    feature.onNetworkSelected(network)
                },
            )

            val listBottomPadding by animateDpAsState(
                targetValue = navBarPadding.calculateBottomPadding() +
                        if (showButton) {
                            88.dp
                        } else {
                            16.dp
                        },
                animationSpec = tween(SAVE_BUTTON_ANIM_MS),
                label = "listBottomPadding",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(bottom = listBottomPadding),
                ) {
                    when {
                        showSkeleton -> {
                            val count = 10
                            items(
                                count,
                                key = { "shimmer_$it" },
                                contentType = { SHIMMER_KEY }) { index ->
                                AccountCellShimmer(
                                    position = defaultBundleType(count, index),
                                    balances = false
                                )
                            }
                        }

                        isEmpty -> item(key = EMPTY_KEY, contentType = { EMPTY_KEY }) {
                            MoonEmptyScreen(
                                text = stringResource(Localization.cant_find_anything),
                                description = if (searchText.value.isEmpty()) {
                                    stringResource(Localization.no_results)
                                } else {
                                    stringResource(Localization.search_no_result, searchText.value)
                                },
                                type = MoonEmptyScreenType.Empty,
                            )
                        }

                        else -> {
                            items(
                                count = pagingItems.itemCount,
                                key = pagingItems.itemKey { it.asset.id },
                                contentType = pagingItems.itemContentType { "account" },
                            ) { index ->
                                val item = pagingItems[index] ?: return@items
                                val isHidden = hiddenOverrides[item.asset.id] ?: item.isHidden
                                fun onClick() {
                                    feature.toggleVisibility(
                                        assetId = item.asset.id,
                                        displayedIsHidden = isHidden,
                                        serverIsHidden = item.isHidden,
                                    )
                                }
                                AccountCell(
                                    account = item,
                                    position = defaultBundleType(pagingItems.itemCount, index),
                                    showVerification = true,
                                    onClick = { onClick() },
                                ) {
                                    MoonItemIcon(
                                        painter = painterResource(
                                            if (isHidden) {
                                                UIKitIcon.ic_eye_closed_outline_28
                                            } else {
                                                UIKitIcon.ic_eye_outline_28
                                            }
                                        ),
                                        color = if (isHidden) {
                                            UIKit.colorScheme.icon.tertiary
                                        } else {
                                            UIKit.colorScheme.accent.blue
                                        },
                                        onClick = { onClick() },
                                    )
                                }
                            }
                            if (pagingItems.loadState.append is LoadState.Loading) {
                                item(LOADING_MORE_KEY) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = UIKit.colorScheme.accent.blue,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                SaveChangesButton(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = showButton,
                    enabled = !isSavingVisibility,
                    onClick = { feature.savePendingVisibilityChanges(onDone = onBack) },
                )
            }
        }
    }

    if (showFilters) {
        FiltersSheet(
            item = FilterSwitchItem(
                title = stringResource(Localization.hide_no_cost_assets),
                subtitle = stringResource(Localization.hide_no_cost_assets_desc),
                checked = hideDust,
                onToggle = { feature.onHideDustChanged() },
            ),
            onClose = { showFilters = false },
        )
    }
}

@Composable
private fun SaveChangesButton(
    visible: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = tween(SAVE_BUTTON_ANIM_MS),
            initialOffsetY = { it },
        ) + fadeIn(animationSpec = tween(SAVE_BUTTON_ANIM_MS)),
        exit = slideOutVertically(
            animationSpec = tween(SAVE_BUTTON_ANIM_MS),
            targetOffsetY = { it },
        ) + fadeOut(animationSpec = tween(SAVE_BUTTON_ANIM_MS)),
    ) {
        MoonBottomButtonCell(
            modifier = Modifier.navigationBarsPadding(),
            text = stringResource(Localization.save_changes),
            enabled = enabled,
            onClick = onClick,
        )
    }
}
