package ui.components.moon.container

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonTopAppBar

@Composable
fun MoonScaffold(
    modifier: Modifier = Modifier,
    title: String,
    onClose: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    MoonScaffold(
        modifier = modifier,
        topBar = {
            MoonTopAppBar(
                title = title,
                actionIconRes = if (onClose == null) null else UIKitIcon.ic_close_16,
                onActionClick = onClose,
                navigationIconRes = if (onBack == null) null else UIKitIcon.ic_chevron_left_16,
                onNavigationClick = onBack,
                ignoreSystemOffset = true,
                showDivider = false,
                backgroundColor = Color.Transparent
            )
        },
        content = content,
    )
}

@Composable
fun MoonScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    MoonSurface(Modifier.fillMaxSize()) {
        Column(modifier) {
            topBar()
            content()
        }
    }
}
