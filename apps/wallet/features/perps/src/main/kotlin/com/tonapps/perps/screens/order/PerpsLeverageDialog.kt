package com.tonapps.perps.screens.order

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.uikit.icon.UIKitIcon
import kotlinx.coroutines.launch
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonDescriptionCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_LEVERAGE = 1
private const val MAX_LEVERAGE = 100
private const val MARK_PRICE = 66141.70
private val TICK_WIDTH = 32.dp

@Composable
fun PerpsLeverageDialog(
    initialLeverage: Int = 27,
    onApply: (leverage: Int) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsLeverageDialogBody(
            initialLeverage = initialLeverage,
            onApply = { leverage ->
                navigator.close()
                onApply(leverage)
            },
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsLeverageDialogBody(
    initialLeverage: Int,
    onApply: (leverage: Int) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val count = MAX_LEVERAGE - MIN_LEVERAGE + 1
    val tickWidthPx = with(LocalDensity.current) { TICK_WIDTH.toPx() }
    val scrollState = rememberScrollState()

    val selectedIndex by remember(tickWidthPx, count) {
        derivedStateOf { (scrollState.value / tickWidthPx).roundToInt().coerceIn(0, count - 1) }
    }
    val leverage = MIN_LEVERAGE + selectedIndex
    val liquidationPrice = MARK_PRICE * (1.0 - 1.0 / leverage)

    LaunchedEffect(Unit) {
        val initialIndex = (initialLeverage - MIN_LEVERAGE).coerceIn(0, count - 1)
        scrollState.scrollTo((initialIndex * tickWidthPx).roundToInt())
    }

    // Snap to the nearest tick once the fling settles.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress) {
            val target = (selectedIndex * tickWidthPx).roundToInt()
            if (scrollState.value != target) {
                scrollState.animateScrollTo(target)
            }
        }
    }

    MoonTopAppBarSimple(
        title = "Leverage",
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
    )

    MoonSurface {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            MoonBundleCell(contentPadding = remember { PaddingValues(vertical = 20.dp) }) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MoonBadgeButton(
                            content = { MoonSmallItemTitle("MIN") },
                            onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                        )
                        Text(
                            text = "${leverage}x",
                            style = remember { TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold) },
                            color = UIKit.colorScheme.text.primary,
                        )
                        MoonBadgeButton(
                            content = { MoonSmallItemTitle("MAX") },
                            onClick = { scope.launch { scrollState.animateScrollTo((count - 1) * tickWidthPx.roundToInt()) } },
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    LeverageRuler(
                        count = count,
                        scrollState = scrollState,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            MoonBundleCell {
                MoonPropertyBigCell(
                    title = {
                        MoonPropertyTitle(
                            title = "Liquidation price",
                            infoTooltip = "If the mark price reaches this level, your position will be liquidated.",
                        )
                    },
                    content = {
                        MoonPropertyValue(title = formatUsdPrice(liquidationPrice))
                    },
                )
            }

            MoonDescriptionCell(
                text = "Setting a higher leverage increases the risk of liquidation.",
            )

            Spacer(Modifier.height(8.dp))

            MoonBottomButtonCell(
                text = "Apply",
                contentPadding = remember { PaddingValues(0.dp) },
            ) {
                onApply(leverage)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LeverageRuler(
    count: Int,
    scrollState: ScrollState,
) {
    val tickWidthPx = with(LocalDensity.current) { TICK_WIDTH.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        val edgePadding = (maxWidth - TICK_WIDTH) / 2

        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Spacer(Modifier.width(edgePadding))
            repeat(count) { index ->
                // 1 when the tick is under the center indicator, fades to 0 within one tick of scroll.
                val selectionProgress by remember(index, tickWidthPx) {
                    derivedStateOf {
                        val distance = abs(index * tickWidthPx - scrollState.value)
                        (1f - distance / tickWidthPx).coerceIn(0f, 1f)
                    }
                }
                LeverageTick(
                    label = (MIN_LEVERAGE + index).toString(),
                    selectionProgress = selectionProgress,
                )
            }
            Spacer(Modifier.width(edgePadding))
        }
    }
}

@Composable
private fun LeverageTick(
    label: String,
    selectionProgress: Float,
) {
    val stickHeight = lerpDp(32.dp, 64.dp, selectionProgress)

    Box(
        modifier = Modifier
            .width(TICK_WIDTH)
            .height(64.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .width(lerpDp(2.dp, 3.dp, selectionProgress))
                .height(stickHeight)
                .clip(CircleShape)
                .background(
                    lerpColor(
                        UIKit.colorScheme.background.contentTint,
                        UIKit.colorScheme.accent.blue,
                        selectionProgress,
                    )
                ),
        )
        Text(
            text = label,
            modifier = Modifier
                .padding(bottom = 6.dp + stickHeight)
                // Fade out twice as fast as the stick grows, fully gone at half progress.
                .alpha((1f - selectionProgress * 4f).coerceAtLeast(0f)),
            style = UIKit.typography.body3,
            color = UIKit.colorScheme.text.tertiary,
        )
    }
}

@Preview
@Composable
private fun PerpsLeverageDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsLeverageDialogBody(
            initialLeverage = 27,
            onApply = {},
            onClose = {},
        )
    }
}
