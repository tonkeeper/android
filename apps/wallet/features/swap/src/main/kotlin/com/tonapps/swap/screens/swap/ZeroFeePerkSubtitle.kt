package com.tonapps.swap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemIcon
import ui.preview.ThemedPreview
import ui.theme.UIKit

// Figma: one-off promo colors, not theme tokens (gradient ends at 78.4%).
private val ZeroFeePerkGradientStart = Color(0xFF24A6FE)
private val ZeroFeePerkGradientEnd = Color(0xFFB0DFFF)
private const val ZeroFeePerkGradientEndStop = 0.784f
private const val ZeroFeePerkSplashPeriodMs = 3000
private const val ZeroFeePerkSplashSweepMs = 1000

@Composable
fun ZeroFeePerkSubtitle() {
    // Splash highlight sweeping across the line every 3s, per the design annotation.
    val splash by rememberInfiniteTransition(label = "zeroFeePerkSplash").animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ZeroFeePerkSplashSweepMs,
                delayMillis = ZeroFeePerkSplashPeriodMs - ZeroFeePerkSplashSweepMs,
                easing = LinearEasing,
            ),
        ),
        label = "zeroFeePerkSplashOffset",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val center = splash * size.width
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.7f),
                            Color.Transparent,
                        ),
                        startX = center - size.width * 0.3f,
                        endX = center + size.width * 0.3f,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            },
    ) {
        MoonItemIcon(
            modifier = Modifier.size(14.dp),
            painter = painterResource(UIKitIcon.ic_flash_24),
            color = ZeroFeePerkGradientStart,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(Localization.raffle_zero_fee_swaps),
            style = UIKit.typography.label2.copy(
                brush = Brush.horizontalGradient(
                    0f to ZeroFeePerkGradientStart,
                    ZeroFeePerkGradientEndStop to ZeroFeePerkGradientEnd,
                    1f to ZeroFeePerkGradientEnd,
                ),
            ),
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun ZeroFeePerkSubtitlePreview() {
    ThemedPreview(isDarkOnly = true) {
        ZeroFeePerkSubtitle()
    }
}
