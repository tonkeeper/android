package ui.components.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonLabel
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
            Text(
                text = action.title.take(18),
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = action.title.take(18),
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                )

                MoonLabel(
                    text = action.badge
                )
            }
        }

        Text(
            text = action.subtitle,
            style = UIKit.typography.body2,
            color = if (action.spam) UIKit.colorScheme.text.tertiary else UIKit.colorScheme.text.secondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        action.warningText?.let {
            Text(
                text = it.take(18),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.accent.orange,
            )
        }
    }
}