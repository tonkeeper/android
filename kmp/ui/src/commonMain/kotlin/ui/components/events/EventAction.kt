package ui.components.events

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.theme.Dimens

@Composable
internal fun EventAction(
    modifier: Modifier = Modifier,
    action: UiEvent.Item.Action,
    index: Int,
    hiddenBalances: Boolean,
    onClick: (part: EventItemClickPart) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        EventActionValues(
            action = action,
            hiddenBalances = hiddenBalances
        )

        if (action.hasAttachments) {
            EventActionAttachments(
                product = action.product,
                text = action.text,
                index = index,
                hiddenBalances = hiddenBalances,
                onClick = onClick
            )
        }
    }
}

@Composable
internal fun EventActionValues(
    action: UiEvent.Item.Action,
    hiddenBalances: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.offsetMedium)
            .padding(top = Dimens.offsetMedium)
    ) {

        EventIconAction(
            action = action
        )

        EventActionInfo(
            action = action,
            modifier = Modifier.weight(1f)
        )

        EventActionAmount(
            incomingAmount = action.incomingAmount,
            outgoingAmount = action.outgoingAmount,
            rightDescription = action.rightDescription,
            date = action.date,
            spam = action.spam,
            hiddenBalances = hiddenBalances
        )
    }
}