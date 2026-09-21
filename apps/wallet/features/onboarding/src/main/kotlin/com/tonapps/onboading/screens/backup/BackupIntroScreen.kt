package com.tonapps.onboading.screens.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonapps.core.extensions.rememberActivity
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar
import ui.components.moon.container.MoonScaffold
import ui.components.moon.moonBottomBarHeight
import ui.painterResource
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun BackupIntroScreen(
    feature: BackupFeature,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val isLoading by feature.isLoading.collectAsState()
    val activity = rememberActivity()

    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        title = "",
        onBack = onBack,
    ) {
        val bottomOverlayHeight = moonBottomBarHeight()

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.offsetLarge)
                    .padding(bottom = bottomOverlayHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(UIKitIcon.ic_write_128),
                    contentDescription = null,
                    tint = UIKit.colorScheme.accent.blue,
                    modifier = Modifier.size(128.dp),
                )
                Spacer(Modifier.height(Dimens.offsetMedium))
                Text(
                    text = stringResource(Localization.setup_finish_backup),
                    style = UIKit.typography.h2,
                    color = UIKit.colorScheme.text.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Dimens.offsetExtraSmall))
                Text(
                    text = stringResource(Localization.setup_finish_backup_subtitle),
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.secondary,
                    textAlign = TextAlign.Center,
                )
            }

            MoonBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                MoonAccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Localization.continue_action),
                    size = ButtonSizeLarge,
                    buttonColors = ButtonColorsPrimary,
                    loading = isLoading,
                    onClick = {
                        feature.loadWords(activity, onContinue)
                    },
                )
            }
        }
    }
}
