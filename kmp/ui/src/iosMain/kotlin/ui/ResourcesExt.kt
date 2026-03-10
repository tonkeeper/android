package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
actual fun painterResource(id: Int): Painter {
    throw IllegalArgumentException("supported only for android")
}

@Composable
actual fun fixAndroidResUrl(url: String): String {
    return url
}