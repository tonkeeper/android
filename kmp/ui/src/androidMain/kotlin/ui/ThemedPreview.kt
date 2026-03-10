package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import ui.theme.LunaTheme
import ui.theme.appColorSchemeBlue
import ui.theme.appColorSchemeDark
import ui.theme.appColorSchemeLight

@Composable
fun ThemedPreview(
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LunaTheme(appColorSchemeBlue()) {
            Surface { content() }
        }

        LunaTheme(appColorSchemeDark()) {
            Surface { content() }
        }

        LunaTheme(appColorSchemeLight()) {
            Surface { content() }
        }
    }
}
