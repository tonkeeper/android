package ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIKitPullToRefresh(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    indicator: @Composable BoxScope.() -> Unit,
    header: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        indicator = indicator,
        content = content
    )
    /*Box {
        header()
        content()
        indicator()
    }*/
}

@Composable
fun UIKitRefreshIndicator(
    modifier: Modifier = Modifier,
) {
    PullToRefreshDefaults.Indicator(
        state = rememberPullToRefreshState(),
        isRefreshing = true,
        color = UIKit.colorScheme.accent.blue
    )
}

