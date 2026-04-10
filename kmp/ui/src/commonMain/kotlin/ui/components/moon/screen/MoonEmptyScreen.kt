package ui.components.moon.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTextButton
import ui.painterResource
import ui.theme.UIKit

@Composable
fun MoonEmptyScreen(
    modifier: Modifier = Modifier,
    text: String,
    buttonText: String? = null,
    onButtonClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = 32.dp)
            .defaultMinSize(minHeight = 350.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_magnifying_glass_16),
            size = 56.dp,
            color = UIKit.colorScheme.icon.secondary
        )

        Text(
            text = text,
            style = UIKit.typography.h3,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 2,
        )

        if (buttonText != null) {
            MoonTextButton(text = buttonText, onClick = onButtonClick)
        }
    }
}