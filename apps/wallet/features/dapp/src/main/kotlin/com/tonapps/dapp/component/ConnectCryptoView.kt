package com.tonapps.dapp.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

private const val CRYPT_PATTERN = "* "
private const val CRYPT_LENGTH = 50
private const val SCROLL_DURATION_MS = 12_000

@Composable
fun ConnectCryptoView(
    key: String,
    modifier: Modifier = Modifier,
) {
    val textColor = UIKit.colorScheme.text.tertiary
    val pageColor = UIKit.colorScheme.background.page
    val cursorColor = UIKit.colorScheme.background.contentTint
    val textStyle = UIKit.typography.body2

    val progress by rememberInfiniteTransition(label = "connectCrypto").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SCROLL_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "connectCryptoProgress",
    )

    val cryptText = remember { CRYPT_PATTERN.repeat(CRYPT_LENGTH) }

    Row(
        modifier = modifier
            .height(32.dp)
            .width(86.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrollingText(
            text = key,
            textStyle = textStyle,
            textColor = textColor,
            progress = progress,
            fadeBrush = Brush.horizontalGradient(listOf(pageColor, Color.Transparent)),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(cursorColor),
        )
        ScrollingText(
            text = cryptText,
            textStyle = textStyle,
            textColor = textColor,
            progress = progress,
            fadeBrush = Brush.horizontalGradient(listOf(Color.Transparent, pageColor)),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun ScrollingText(
    text: String,
    textStyle: TextStyle,
    textColor: Color,
    progress: Float,
    fadeBrush: Brush,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val layout = remember(text, textStyle, textColor) {
        measurer.measure(
            text = AnnotatedString(text),
            style = textStyle.copy(color = textColor),
            softWrap = false,
        )
    }
    Canvas(modifier = modifier) {
        val textWidth = layout.size.width.toFloat()
        if (textWidth <= 0f) return@Canvas
        val offsetX = progress * textWidth
        val offsetY = (size.height - layout.size.height) / 2f
        clipRect {
            drawText(layout, topLeft = Offset(offsetX, offsetY))
            drawText(layout, topLeft = Offset(offsetX - textWidth, offsetY))
        }
        drawRect(brush = fadeBrush)
    }
}
