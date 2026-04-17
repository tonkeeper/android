package ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import ui.components.moon.container.MoonSurface
import ui.theme.MoonTheme
import ui.theme.appColorSchemeDark
import ui.theme.appColorSchemeLight

@Composable
fun ThemedPreview(
    isDarkOnly: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
//        MoonTheme(appColorSchemeBlue()) {
//            MoonSurface(shape = RectangleShape) { content() }
//        }

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
