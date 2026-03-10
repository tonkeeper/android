package ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter

@Immutable
data class ComposeIcon(
    val url: String,
    val tintColor: Int? = null,
) {

    val colorFilter: ColorFilter?
        get() {
            val color = tintColor ?: return null
            return ColorFilter.tint(Color(color))
        }
}