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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonLottie
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Immutable
enum class MoonEmptyScreenType {
    Empty,
    EmptyActivity,
    EmptyApps,
    Error,
}

@Composable
fun MoonEmptyScreen(
    modifier: Modifier = Modifier,
    text: String,
    description: String? = null,
    type: MoonEmptyScreenType = MoonEmptyScreenType.Empty,
    buttonText: String? = null,
    buttonIconRes: Int? = UIKitIcon.ic_refresh_16,
    onButtonClick: () -> Unit = {},
    minHeight: Dp? = 350.dp,
) {
    val navBarPadding = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues()

    val lottieFileName = when (type) {
        MoonEmptyScreenType.Empty -> "magnifying_glass.json"
        MoonEmptyScreenType.EmptyActivity -> "clock.json"
        MoonEmptyScreenType.EmptyApps -> "ic_apps_28_opt.json"
        MoonEmptyScreenType.Error -> "exclamationmark_circle.json"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .padding(bottom = navBarPadding.calculateBottomPadding())
            .then(
                if (minHeight != null) {
                    Modifier.defaultMinSize(minHeight = minHeight)
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        MoonLottie(
            fileName = lottieFileName,
            iterations = 1,
            modifier = Modifier.size(56.dp),
            color = UIKit.colorScheme.accent.blue,
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
                icon = buttonIconRes?.let { iconRes ->
                    {
                        MoonItemIcon(
                            painter = painterResource(iconRes),
                            color = ButtonColorsSecondary.contentColor,
                        )
                    }
                },
                buttonColors = ButtonColorsSecondary,
                onClick = onButtonClick,
            )
        }
    }
}

@Preview
@Composable
private fun MoonEmptyScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        MoonEmptyScreen(
            text = "Nothing found",
            description = "Try a different search query or check back later",
            buttonText = "Refresh",
        )
    }
}

@Preview
@Composable
private fun MoonEmptyScreenErrorPreview() {
    ThemedPreview(isDarkOnly = true) {
        MoonEmptyScreen(
            text = "Something went wrong",
            type = MoonEmptyScreenType.Error,
            buttonText = "Try again",
        )
    }
}
