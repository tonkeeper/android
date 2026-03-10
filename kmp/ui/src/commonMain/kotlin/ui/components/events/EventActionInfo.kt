package ui.components.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.TKBadge
import ui.components.base.SimpleText
import ui.theme.UIKit

@Composable
internal fun EventActionInfo(
    action: UiEvent.Item.Action,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {

        if (action.badge == null) {
            SimpleText(
                text = action.title.take(18),
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SimpleText(
                    text = action.title.take(18),
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                )

                TKBadge(
                    text = action.badge
                )
            }
        }

        SimpleText(
            text = action.subtitle,
            style = UIKit.typography.body2,
            color = if (action.spam) UIKit.colorScheme.text.tertiary else UIKit.colorScheme.text.secondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        action.warningText?.let {
            SimpleText(
                text = it.take(18),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.accent.orange,
            )
        }
    }
}