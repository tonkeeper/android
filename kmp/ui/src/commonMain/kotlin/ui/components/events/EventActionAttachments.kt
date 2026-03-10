package ui.components.events

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.theme.Dimens

@Composable
internal fun EventActionAttachments(
    product: UiEvent.Item.Action.Product?,
    text: UiEvent.Item.Action.Text?,
    index: Int,
    hiddenBalances: Boolean,
    onClick: (part: EventItemClickPart) -> Unit
) {

    val productClick = remember(index, onClick) { { onClick(EventItemClickPart.Product(index)) } }

    if (product != null) {
        EventActionProduct(
            modifier = Modifier.padding(
                start = 76.dp,
                top = 8.dp,
                end = Dimens.offsetMedium
            ),
            product = product,
            hiddenBalances = hiddenBalances,
            onClick = productClick
        )
    }

    if (text != null) {
        EventActionText(
            modifier = Modifier.padding(
                start = 76.dp,
                top = 8.dp,
                end = Dimens.offsetMedium
            ),
            state = text,
            index = index,
            onClick = onClick
        )
    }
}