package ui.components.moon

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import ui.theme.UIKit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonRefresh(
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState()
) {
    val fraction = state.distanceFraction.coerceIn(0f, 1f)
    val scale = fraction * fraction

    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = true,
        modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        color = UIKit.colorScheme.accent.blue
    )
}
