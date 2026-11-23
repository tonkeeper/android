package com.tonapps.wallet.features.ledger.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.uikit.icon.UIKitIcon
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
internal fun LedgerDevicePicture(displayText: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.aspectRatio(DEVICE_WIDTH / DEVICE_HEIGHT)) {
        val scale = maxWidth.value / DEVICE_WIDTH
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(UIKitIcon.img_ledger_device),
            contentDescription = null,
        )
        Box(
            modifier = Modifier
                .padding(start = (DISPLAY_START * scale).dp, top = (DISPLAY_TOP * scale).dp)
                .size(width = (DISPLAY_WIDTH * scale).dp, height = (DISPLAY_HEIGHT * scale).dp)
                .background(UIKit.colorScheme.background.content, RoundedCornerShape((DISPLAY_CORNER * scale).dp)),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                modifier = Modifier.fillMaxSize(),
                targetState = displayText,
                animationSpec = tween(FADE_MILLIS),
                label = "display",
            ) { text ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = text,
                        style = UIKit.typography.mono.copy(fontSize = (FONT_SIZE * scale).sp, lineHeight = (LINE_HEIGHT * scale).sp),
                        color = UIKit.colorScheme.text.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LedgerDevicePicturePreview() {
    ThemedPreview {
        LedgerDevicePicture(displayText = "Ethereum")
    }
}

private const val DEVICE_WIDTH = 353f
private const val DEVICE_HEIGHT = 56f
private const val DISPLAY_START = 52f
private const val DISPLAY_TOP = 8f
private const val DISPLAY_WIDTH = 89f
private const val DISPLAY_HEIGHT = 40f
private const val DISPLAY_CORNER = 4f
private const val FONT_SIZE = 12f
private const val LINE_HEIGHT = 16f
private const val FADE_MILLIS = 300
