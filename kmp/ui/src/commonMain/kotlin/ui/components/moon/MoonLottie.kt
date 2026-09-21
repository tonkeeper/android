package ui.components.moon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
expect fun MoonLottie(
    fileName: String,
    modifier: Modifier = Modifier,
    iterations: Int = Int.MAX_VALUE,
    contentDescription: String? = null,
    color: Color? = null,
    progress: (() -> Float)? = null,
)
