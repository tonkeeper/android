package ui.components.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@OptIn(ExperimentalLayoutApi::class)
@Composable
@NonRestartableComposable
fun UIKitScaffold(
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackBarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val safeInsets = remember(contentWindowInsets) {
        MutableWindowInsets(contentWindowInsets)
    }

    Box(
        modifier = modifier.onConsumedWindowInsetsChanged { consumedWindowInsets ->
            safeInsets.insets = contentWindowInsets.exclude(consumedWindowInsets)
        }
    ) {
        UIKitScaffoldLayout(
            topBar = topBar,
            content = content,
            snackBar = snackBarHost,
            contentWindowInsets = safeInsets,
            bottomBar = bottomBar,
        )
    }
}