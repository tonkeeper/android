package ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

@Composable
@ReadOnlyComposable
fun String.uppercased(): String {
    return uppercase()
}
