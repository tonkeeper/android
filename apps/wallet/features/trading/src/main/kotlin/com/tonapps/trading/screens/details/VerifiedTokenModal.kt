package com.tonapps.trading.screens.details

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonLottie
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview

@Composable
internal fun VerifiedTokenModal(onDismiss: () -> Unit) {
    val navigator = rememberDialogNavigator(onClose = onDismiss)

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )
        MoonLottie(
            fileName = "verification_checkmark.json",
            iterations = 1,
            modifier = Modifier.size(84.dp),
        )
        Spacer(modifier = Modifier.height(13.dp))
        MoonTextContentCell(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = stringResource(Localization.verified_token),
            description = stringResource(Localization.verified_token_caption),
        )
        Spacer(modifier = Modifier.height(16.dp))
        MoonAccentButton(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            text = stringResource(Localization.ok),
            size = ButtonSizeLarge,
            onClick = { navigator.close() },
        )
    }
}

@Preview
@Composable
private fun VerifiedTokenModalPreview() {
    ThemedPreview {
        VerifiedTokenModal(onDismiss = {})
    }
}
