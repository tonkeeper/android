package ui.components.modal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.theme.Dimens

@Composable
fun TKModalScaffold(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    headerLeftButton: @Composable () -> Unit = {},
    onCloseClick: () -> Unit,
    actionBar: @Composable (BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            .wrapContentHeight()
            .padding(Dimens.offsetMedium),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment
    ) {
        TKModalHeader(
            leftButton = headerLeftButton,
            onCloseClick = onCloseClick
        )
        content()
        actionBar?.let { layout ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.heightActionBar),
                contentAlignment = Alignment.Center
            ) {
                layout()
            }
        }
    }
}