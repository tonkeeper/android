package ui.theme.modifiers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
inline fun Modifier.modifyIf(modifier: @Composable Modifier.() -> Modifier?): Modifier {
    return modifier.invoke(this)
        ?: this
}
