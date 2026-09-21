package ui.components.moon.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonLoader

@Composable
fun MoonLoadingScreen(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = 32.dp)
            .defaultMinSize(minHeight = 350.dp),
        contentAlignment = Alignment.Center,
    ) {
        MoonLoader(
            modifier = Modifier.size(24.dp),
        )
    }
}
