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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.tonapps.perps.data.MIN_LEVERAGE
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.perpsLiquidationPrice
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.RStr
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonDescriptionCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.utils.uppercased
import java.math.BigDecimal
import kotlin.math.roundToInt

private val TICK_SPACING = 26.dp
private val TICK_WIDTH = 1.5.dp
private val TICK_HEIGHT = 32.dp
private val INDICATOR_WIDTH = 3.dp
private val RULER_HEIGHT = 64.dp

@Composable
fun PerpsLeverageDialog(
    initialLeverage: Int,
    rules: PerpsMarketRules,
    side: PerpsOrderSide,
    markPrice: BigDecimal?,
    entryPrice: BigDecimal?,
    onApply: (leverage: Int) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsLeverageDialogBody(
            initialLeverage = initialLeverage,
            rules = rules,
            side = side,
            markPrice = markPrice,
            entryPrice = entryPrice,
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
    rules: PerpsMarketRules,
    side: PerpsOrderSide,
    markPrice: BigDecimal?,
    entryPrice: BigDecimal?,
    onApply: (leverage: Int) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val count = rules.maxLeverage - MIN_LEVERAGE + 1
    val tickSpacingPx = with(LocalDensity.current) { TICK_SPACING.toPx() }
    val scrollState = rememberScrollState()

    val selectedIndex by remember(tickSpacingPx, count) {
        derivedStateOf { (scrollState.value / tickSpacingPx).roundToInt().coerceIn(0, count - 1) }
    }
    val leverage = MIN_LEVERAGE + selectedIndex

    LaunchedEffect(Unit) {
        val initialIndex = (initialLeverage - MIN_LEVERAGE).coerceIn(0, count - 1)
        scrollState.scrollTo((initialIndex * tickSpacingPx).roundToInt())
    }

    LaunchedEffect(Unit) {
        snapshotFlow { selectedIndex }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    // Snap to the nearest tick once the fling settles.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress) {
            val target = (selectedIndex * tickSpacingPx).roundToInt()
            if (scrollState.value != target) {
                scrollState.animateScrollTo(target)
            }
        }
    }

    val liquidationText = perpsLiquidationPrice(
        side = side,
        entryPrice = entryPrice,
        markPrice = markPrice,
        leverage = leverage,
        maintenanceMarginBps = rules.maintenanceMarginBps,
    )?.let { formatUsdPrice(it.toDouble(), rules.priceDecimals) } ?: PERPS_EMPTY_VALUE

    MoonTopAppBarSimple(
        title = stringResource(Localization.perps_leverage_title),
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
    )

    MoonSurface {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            MoonBundleCell(contentPadding = remember { PaddingValues(vertical = 16.dp) }) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MoonBadgeButton(
                            content = { MoonSmallItemTitle(stringResource(Localization.perps_leverage_min).uppercased()) },
                            onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                        )
                        Text(
                            text = stringResource(Localization.perps_leverage_value_pattern, leverage),
                            style = remember { TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold) },
                            color = UIKit.colorScheme.text.primary,
                        )
                        MoonBadgeButton(
                            content = { MoonSmallItemTitle(stringResource(RStr.max).uppercased()) },
                            onClick = { scope.launch { scrollState.animateScrollTo((count - 1) * tickSpacingPx.roundToInt()) } },
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    LeverageRuler(
                        count = count,
                        selectedIndex = selectedIndex,
                        scrollState = scrollState,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            MoonBundleCell {
                MoonPropertyBigCell(
                    title = {
                        MoonPropertyTitle(
                            title = stringResource(Localization.perps_liquidation_price),
                            infoTooltip = stringResource(Localization.perps_liquidation_price_tooltip),
                        )
                    },
                    content = { MoonItemTitle(text = liquidationText) },
                )
            }

            MoonDescriptionCell(
                text = stringResource(Localization.perps_leverage_warning),
            )

            MoonBottomButtonCell(
                text = stringResource(Localization.perps_leverage_apply),
            ) {
                onApply(leverage)
            }
        }
    }
}

@Composable
private fun LeverageRuler(
    count: Int,
    selectedIndex: Int,
    scrollState: ScrollState,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(brush = RULER_FADE, blendMode = BlendMode.DstIn)
            },
    ) {
        val edgePadding = (maxWidth - TICK_SPACING) / 2

        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Spacer(Modifier.width(edgePadding))
            repeat(count) { index ->
                LeverageTick(
                    label = (MIN_LEVERAGE + index).toString(),
                    // The centred tick is replaced by the indicator.
                    isSelected = index == selectedIndex,
                )
            }
            Spacer(Modifier.width(edgePadding))
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(INDICATOR_WIDTH)
                .height(RULER_HEIGHT)
                .clip(CircleShape)
                .background(UIKit.colorScheme.accent.blue),
        )
    }
}

@Composable
private fun LeverageTick(
    label: String,
    isSelected: Boolean,
) {
    Column(
        modifier = Modifier
            .width(TICK_SPACING)
            .height(RULER_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (!isSelected) {
            Text(
                text = label,
                style = UIKit.typography.body4CAPS,
                color = UIKit.colorScheme.text.secondary,
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(TICK_WIDTH)
                    .height(TICK_HEIGHT)
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.icon.tertiary),
            )
        }
    }
}

private val RULER_FADE = Brush.horizontalGradient(
    0f to Color.Transparent,
    0.22f to Color.Black,
    0.78f to Color.Black,
    1f to Color.Transparent,
)

@Preview
@Composable
private fun PerpsLeverageDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsLeverageDialogBody(
            initialLeverage = 20,
            rules = previewRules,
            side = PerpsOrderSide.LONG,
            markPrice = previewRules.markPrice,
            entryPrice = previewRules.markPrice,
            onApply = {},
            onClose = {},
        )
    }
}
