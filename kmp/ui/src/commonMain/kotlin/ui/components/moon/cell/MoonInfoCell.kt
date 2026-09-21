package ui.components.moon.cell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.components.popup.MenuPosition
import ui.painterResource
import ui.theme.UIKit

@Composable
fun MoonWarningCell(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_information_circle_12),
            color = UIKit.colorScheme.icon.tertiary,
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = text,
            color = UIKit.colorScheme.text.secondary,
            style = UIKit.typography.body2,
        )
    }
}