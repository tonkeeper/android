package ui.components.moon.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.painterResource
import ui.theme.UIKit

@Composable
fun MoonEmptyScreen(
    modifier: Modifier = Modifier,
    text: String,
    description: String? = null,
    iconRes: Int = UIKitIcon.ic_magnifying_glass_28,
    buttonText: String? = null,
    buttonIconRes: Int = UIKitIcon.ic_refresh_16,
    onButtonClick: () -> Unit = {},
) {
    val navBarPadding = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues()

    Column(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = navBarPadding.calculateBottomPadding())
            .defaultMinSize(minHeight = 350.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        MoonItemIcon(
            painter = painterResource(iconRes),
            size = 56.dp,
            color = UIKit.colorScheme.accent.blue
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                style = UIKit.typography.h3,
                color = UIKit.colorScheme.text.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (buttonText != null) {
            MoonAccentButton(
                text = buttonText,
                size = ButtonSizeSmall,
                icon = {
                    MoonItemIcon(
                        painter = painterResource(buttonIconRes),
                        color = ButtonColorsSecondary.contentColor,
                    )
                },
                buttonColors = ButtonColorsSecondary,
                onClick = onButtonClick,
            )
        }
    }
}