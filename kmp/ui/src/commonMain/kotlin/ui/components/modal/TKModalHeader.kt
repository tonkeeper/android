package ui.components.modal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.components.ActionButtonIcon
import ui.theme.Dimens
import ui.theme.resources.Res
import ui.theme.resources.ic_close_16

@Composable
fun TKModalHeader(
    modifier: Modifier = Modifier,
    leftButton: @Composable () -> Unit = {},
    onCloseClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.sizeAction),
    ) {
        leftButton()

        Spacer(Modifier.weight(1f))

        ActionButtonIcon(
            painter = painterResource(Res.drawable.ic_close_16),
            onClick = onCloseClick,
        )
    }
}