package com.tonapps.tonkeeper.ui.screen.browser.safe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonTopAppBar
import ui.components.moon.ButtonColorsSecondary
import ui.theme.Dimens

@Composable
fun DAppSafeComposable(
    onSafeClick: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.offsetMedium)
            .padding(bottom = Dimens.offsetMedium)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(Dimens.offsetMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MoonTopAppBar(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = onClose,
            ignoreSystemOffset = true,
            showDivider = false,
            backgroundColor = Color.Transparent
        )

        Image(
            modifier = Modifier.size(96.dp),
            painter = painterResource(id = UIKitIcon.ic_exclamationmark_triangle_84),
            contentDescription = null,
        )

        MoonTextContentCell(
            title = stringResource(id = Localization.dapp_safe_modal_title),
            description = stringResource(id = Localization.dapp_safe_modal_subtitle),
        )

        Spacer(modifier = Modifier.height(Dimens.offsetExtraSmall))

        MoonAccentButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSafeClick,
            text = stringResource(id = Localization.dapp_safe_model_button),
            buttonColors = ButtonColorsSecondary
        )

        MoonAccentButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClose,
            text = stringResource(id = Localization.close),
            buttonColors = ButtonColorsSecondary
        )

        Spacer(modifier = Modifier.height(Dimens.offsetExtraSmall))
    }
}
