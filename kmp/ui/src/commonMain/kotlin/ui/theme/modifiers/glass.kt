package ui.theme.modifiers

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.color.GlassColorMath
import com.tonapps.uikit.color.GlassSpec
import com.tonapps.uikit.color.GlassStyle
import com.tonapps.uikit.icon.UIKitIcon
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun Modifier.glass(
    style: GlassStyle,
    shape: Shape = CircleShape,
    tint: Color = Color.Unspecified,
): Modifier {
    val colorScheme = UIKit.colorScheme
    val glassColors = colorScheme.glass
    val fillTint = tint.takeOrElse {
        when (style) {
            GlassStyle.Regular -> colorScheme.background.content
            GlassStyle.Prominent -> colorScheme.buttonPrimary.primaryBackground
        }
    }
    val page = colorScheme.background.page
    val fillBrush = remember(style, fillTint, page) {
        val stops = GlassColorMath.fillStops(style, fillTint.toArgb(), page.toArgb())
        Brush.verticalGradient(*GlassColorMath.fillPositions().zip(stops.map(::Color)).toTypedArray())
    }
    val rimBrushStops = remember(glassColors) {
        val colors = listOf(
            glassColors.rimTop,
            glassColors.rimTop.copy(alpha = 0f),
            glassColors.rimBottom.copy(alpha = 0f),
            glassColors.rimBottom,
        )
        GlassSpec.rimPositions().zip(colors).toTypedArray()
    }
    val isBorderVisible = style == GlassStyle.Regular && glassColors.border.alpha > 0f
    val elevation = if (style == GlassStyle.Prominent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        GlassSpec.PROMINENT_ELEVATION_DP.dp
    } else {
        0.dp
    }

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = glassColors.shadow,
            spotColor = glassColors.shadow,
        )
        .drawWithCache {
            val fillOutline = shape.createOutline(size, layoutDirection, this)
            val borderWidthPx = GlassSpec.BORDER_WIDTH_DP.dp.toPx()
            val borderInset = borderWidthPx / 2f
            val borderOutline = shape.createOutline(
                Size((size.width - borderWidthPx).coerceAtLeast(0f), (size.height - borderWidthPx).coerceAtLeast(0f)),
                layoutDirection,
                this
            )
            val rimWidthPx = GlassSpec.RIM_WIDTH_DP.dp.toPx()
            val rimInset = rimWidthPx / 2f
            val rimOutline = shape.createOutline(
                Size((size.width - rimWidthPx).coerceAtLeast(0f), (size.height - rimWidthPx).coerceAtLeast(0f)),
                layoutDirection,
                this
            )
            val rimBrush = Brush.verticalGradient(
                *rimBrushStops,
                startY = -rimInset,
                endY = size.height - rimInset,
            )
            val fitsBorder = isBorderVisible && size.minDimension >= borderWidthPx
            val fitsRim = size.minDimension >= rimWidthPx
            onDrawBehind {
                drawOutline(fillOutline, fillBrush)
                if (fitsBorder) {
                    translate(borderInset, borderInset) {
                        drawOutline(borderOutline, glassColors.border, style = Stroke(borderWidthPx))
                    }
                }
                if (fitsRim) {
                    translate(rimInset, rimInset) {
                        drawOutline(rimOutline, rimBrush, style = Stroke(rimWidthPx))
                    }
                }
            }
        }
}

@Composable
private fun GlassSamples() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .glass(GlassStyle.Regular)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .glass(GlassStyle.Prominent)
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .glass(GlassStyle.Regular),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UIKitIcon.ic_chevron_left_28),
                contentDescription = null,
                tint = UIKit.colorScheme.icon.primary,
            )
        }
    }
}

@Preview
@Composable
private fun GlassPreview() {
    ThemedPreview(glass = true) {
        GlassSamples()
    }
}

@Preview
@Composable
private fun GlassRtlPreview() {
    ThemedPreview(glass = true, layoutDirection = LayoutDirection.Rtl) {
        GlassSamples()
    }
}
