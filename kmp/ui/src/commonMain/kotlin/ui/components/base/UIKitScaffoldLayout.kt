package ui.components.base

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy

@Composable
fun UIKitScaffoldLayout(
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    snackBar: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    bottomBar: @Composable () -> Unit
) {
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val topBarPlaceables = subcompose(UIKitScaffoldLayoutContent.TopBar, topBar).fastMap {
            it.measure(looseConstraints)
        }

        val topBarHeight = topBarPlaceables.fastMaxBy { it.height }?.height ?: 0

        val snackBarPlaceables = subcompose(UIKitScaffoldLayoutContent.SnackBar, snackBar).fastMap {
            val leftInset = contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection)
            val rightInset = contentWindowInsets.getRight(this@SubcomposeLayout, layoutDirection)
            val bottomInset = contentWindowInsets.getBottom(this@SubcomposeLayout)
            it.measure(looseConstraints.offset(-leftInset - rightInset, -bottomInset))
        }


        val snackBarHeight = snackBarPlaceables.fastMaxBy { it.height }?.height ?: 0
        val snackBarWidth = snackBarPlaceables.fastMaxBy { it.width }?.width ?: 0


        val bottomBarPlaceables = subcompose(UIKitScaffoldLayoutContent.BottomBar) {
            bottomBar()
        }.fastMap { it.measure(looseConstraints) }


        val bottomBarHeight = bottomBarPlaceables.fastMaxBy { it.height }?.height

        val snackBarOffsetFromBottom = if (snackBarHeight != 0) {
            snackBarHeight + contentWindowInsets.getBottom(this@SubcomposeLayout)
        } else {
            0
        }


        val bodyContentPlaceables = subcompose(UIKitScaffoldLayoutContent.MainContent) {
            val insets = contentWindowInsets.asPaddingValues(this@SubcomposeLayout)
            val innerPadding =
                PaddingValues(
                    top =
                        if (topBarPlaceables.isEmpty()) {
                            insets.calculateTopPadding()
                        } else {
                            topBarHeight.toDp()
                        },
                    bottom =
                        if (bottomBarPlaceables.isEmpty() || bottomBarHeight == null) {
                            insets.calculateBottomPadding()
                        } else {
                            bottomBarHeight.toDp()
                        },
                    start =
                        insets.calculateStartPadding(
                            (this@SubcomposeLayout).layoutDirection
                        ),
                    end =
                        insets.calculateEndPadding((this@SubcomposeLayout).layoutDirection)
                )
            content(innerPadding)
        }.fastMap { it.measure(looseConstraints) }

        layout(layoutWidth, layoutHeight) {
            bodyContentPlaceables.fastForEach { it.place(0, 0) }
            topBarPlaceables.fastForEach { it.place(0, 0) }
            snackBarPlaceables.fastForEach {
                it.place(
                    (layoutWidth - snackBarWidth) / 2 + contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection),
                    layoutHeight - snackBarOffsetFromBottom
                )
            }
            bottomBarPlaceables.fastForEach { it.place(0, layoutHeight - (bottomBarHeight ?: 0)) }
        }
    }
}

private enum class UIKitScaffoldLayoutContent {
    TopBar,
    MainContent,
    SnackBar,
    BottomBar
}