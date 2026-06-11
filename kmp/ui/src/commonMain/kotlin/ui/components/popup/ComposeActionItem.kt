package ui.components.popup

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter
import ui.ComposeIcon

@Immutable
data class ComposeActionItem(
    val id: String,
    val text: String,
    val icon: ComposeIcon? = null,
    val iconPainter: Painter? = null,
)