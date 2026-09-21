package com.tonapps.core.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.emoji.ui.EmojiView
import ui.theme.UIKit

@Composable
fun Emoji(
    modifier: Modifier = Modifier,
    emoji: CharSequence,
    size: Dp = 20.dp,
) {
    val tintColor = UIKit.colorScheme.icon.primary.toArgb()
    AndroidView(
        modifier = modifier.size(size),
        factory = { context -> EmojiView(context) },
        update = { view ->
            view.setEmoji(emoji, tintColor)
        },
    )
}
