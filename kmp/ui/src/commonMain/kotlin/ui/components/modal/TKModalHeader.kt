package ui.components.modal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import ui.components.moon.MoonActionIcon
import ui.theme.Dimens
import ui.theme.resources.Res
import ui.theme.resources.ic_close_16

@Deprecated("Use moon")
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

        MoonActionIcon(
            painter = painterResource(Res.drawable.ic_close_16),
            onClick = onCloseClick,
        )
    }
}