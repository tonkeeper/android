package ui.components.bar.top

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import ui.components.base.SimpleText
import ui.theme.Dimens
import ui.theme.UIKit
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@NonRestartableComposable
fun LargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
) {
    val expandedHeight = 84.dp
    val density = LocalDensity.current
    val expandedPx = with(density) { expandedHeight.toPx().coerceAtLeast(0f) }
    val paddingHorizontalPx = with(density) { Dimens.offsetMedium.toPx().roundToInt() }

    LaunchedEffect(scrollBehavior, expandedPx) {
        if (scrollBehavior.state.heightOffsetLimit != -expandedPx) {
            scrollBehavior.state.heightOffsetLimit = -expandedPx
        }
    }

    Layout(
        modifier = modifier
            .windowInsetsPadding(windowInsets)
            .clipToBounds(),
        content = {
            SimpleText(
                text = title,
                style = UIKit.typography.h1,
                color = UIKit.colorScheme.text.primary
            )
        }
    ) { measurables, constraints ->
        val base = expandedPx
        val offset = scrollBehavior.state.heightOffset
        val currentHeight = (base + offset).coerceIn(0f, base).roundToInt()

        val maxHForChild = min(constraints.maxHeight, expandedPx.roundToInt())
        val childConstraints = constraints
            .offset(horizontal = -2 * paddingHorizontalPx)
            .copy(maxHeight = maxHForChild)

        val childPlaceable = measurables[0].measure(childConstraints)
        val width = constraints.maxWidth

        layout(width, currentHeight) {
            if (currentHeight > 0) {
                val x = paddingHorizontalPx
                val y = currentHeight - childPlaceable.height
                childPlaceable.place(x, y)
            }
        }
    }
}