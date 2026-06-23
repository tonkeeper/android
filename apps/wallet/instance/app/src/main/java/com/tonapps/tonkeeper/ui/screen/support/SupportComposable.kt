package com.tonapps.tonkeeper.ui.screen.support

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonTextContentCell
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
private fun SupportIcon() {
    Icon(
        modifier = Modifier.size(72.dp),
        painter = painterResource(id = UIKitIcon.ic_message_bubble_28),
        contentDescription = null,
        tint = UIKit.colorScheme.icon.primary,
    )
}

@Composable
fun SupportComposable(
    onTelegramClick: () -> Unit,
    onEmailClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        MoonTopAppBar(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { onCloseClick() },
            ignoreSystemOffset = true,
            showDivider = false,
            backgroundColor = Color.Transparent
        )

        Column(
            modifier = Modifier
                .padding(horizontal = Dimens.offsetMedium)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SupportIcon()
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.padding(horizontal = Dimens.offsetMedium)) {
                MoonTextContentCell(
                    title = stringResource(id = Localization.support_title),
                    description = stringResource(id = Localization.support_subtitle),
                )
            }
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
            MoonAccentButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onTelegramClick,
                text = stringResource(id = Localization.ask_question),
                buttonColors = ButtonColorsPrimary,
                size = ButtonSizeLarge,
                icon = {
                    MoonItemIcon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(id = R.drawable.ic_telegram_16),
                        contentDescription = null,
                        color = LocalContentColor.current,
                    )
                },
            )
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            MoonAccentButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onEmailClick,
                text = stringResource(id = Localization.email_us),
                buttonColors = ButtonColorsSecondary,
                size = ButtonSizeLarge,
                icon = {
                    MoonItemIcon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(id = R.drawable.ic_envelope_16),
                        contentDescription = null,
                        color = LocalContentColor.current,
                    )
                },
            )
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
        }
    }
}
