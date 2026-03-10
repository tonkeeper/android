package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun painterResource(id: Int): Painter

@Composable
expect fun fixAndroidResUrl(url: String): String