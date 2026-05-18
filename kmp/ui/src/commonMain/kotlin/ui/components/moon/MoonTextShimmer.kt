package ui.components.moon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import ui.theme.Dimens
import ui.theme.UIKit
import ui.theme.modifiers.shimmer

/**
 * Shimmer placeholder that mimics a real `Text`.
 *
 * The composable reserves the same layout footprint as the actual `Text`
 * rendered with [style] (including line-height / leading and Android
 * `includeFontPadding`), so toggling between loading and loaded states does
 * not shift surrounding content. The visible shimmer pill, however, is sized
 * to the glyph cap height (`style.fontSize`) and centered within that
 * footprint — visually matching the real letters' bounds rather than the
 * full line-box.
 *
 * @param text Sample string used solely to measure the placeholder width.
 * @param style Typography style; should match the real `Text` it replaces.
 * @param phase Shimmer phase (`0f..1f`). Defaults to `0f` — pass an animated
 *   value (e.g. from `rememberShimmerPhase()`) to enable the sweep animation.
 * @param cornerRadius Corner radius of the shimmer rectangle. Defaults to
 *   [Dimens.cornerMedium].
 * @param backgroundFill Base color of the shimmer pill.
 * @param highlightColor Highlight color that sweeps across the pill.
 */
@Composable
fun MoonTextShimmer(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    phase: Float = 0f,
    cornerRadius: Dp = Dimens.cornerMedium,
    backgroundFill: Color = UIKit.colorScheme.background.content,
    highlightColor: Color = UIKit.colorScheme.background.contentTint,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val measured = remember(text, style, density) {
        measurer.measure(text = AnnotatedString(text), style = style)
    }

    val outerWidth: Dp = with(density) { measured.size.width.toDp() }
    val outerHeight: Dp = with(density) { measured.size.height.toDp() }
    val shimmerHeight: Dp = with(density) {
        if (style.fontSize.isSpecified) style.fontSize.toDp() else outerHeight
    }

    Box(
        modifier = modifier
            .width(outerWidth)
            .height(outerHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(shimmerHeight)
                .shimmer(
                    phase = phase,
                    cornerRadius = cornerRadius,
                    backgroundFill = backgroundFill,
                    highlightColor = highlightColor,
                ),
        )
    }
}
