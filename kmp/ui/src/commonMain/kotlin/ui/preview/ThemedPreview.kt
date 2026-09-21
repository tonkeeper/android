package ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ui.components.moon.container.MoonSurface
import ui.theme.MoonTheme
import ui.theme.appColorSchemeBlue
import ui.theme.appColorSchemeDark
import ui.theme.appColorSchemeLight

@Composable
fun ThemedPreview(
    isDarkOnly: Boolean = false,
    layoutDirection: LayoutDirection = LocalLayoutDirection.current,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MoonTheme(appColorSchemeBlue()) {
            MoonSurface(shape = RectangleShape) { content() }
        }

        if (!isDarkOnly) {
            MoonTheme(appColorSchemeDark()) {
                MoonSurface(shape = RectangleShape) { content() }
            }
        }

//        if (!isDarkOnly) {
            MoonTheme(appColorSchemeLight()) {
                MoonSurface(shape = RectangleShape) { content() }
            }
//        }
    }
}
