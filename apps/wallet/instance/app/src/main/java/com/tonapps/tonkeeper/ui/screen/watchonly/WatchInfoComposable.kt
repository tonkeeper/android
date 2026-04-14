package com.tonapps.tonkeeper.ui.screen.watchonly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonTopAppBar
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun SupportComposable(
    onRecoveryClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        MoonTopAppBar(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { onContinueClick() },
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
            Icon(
                painter = ui.painterResource(id = UIKitIcon.ic_exclamationmark_circle_84),
                contentDescription = null,
                tint = UIKit.colorScheme.accent.blue
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.padding(horizontal = Dimens.offsetLarge)) {
                MoonTextContentCell(
                    title = stringResource(id = Localization.watch_only_info_title),
                    description = stringResource(id = Localization.watch_only_info_subtitle),
                )
            }
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
            MoonAccentButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRecoveryClick,
                text = stringResource(id = Localization.enter_recovery_phrase),
                buttonColors = ButtonColorsPrimary,
                size = ButtonSizeLarge,
            )
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            MoonAccentButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinueClick,
                text = stringResource(id = Localization.continue_watch_account),
                buttonColors = ButtonColorsSecondary,
                size = ButtonSizeLarge,
            )
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
        }
    }
}
