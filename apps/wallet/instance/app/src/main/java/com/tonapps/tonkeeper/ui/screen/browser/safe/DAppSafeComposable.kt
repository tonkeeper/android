package com.tonapps.tonkeeper.ui.screen.browser.safe

import android.net.Uri
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import uikit.compose.AppTheme
import uikit.compose.Dimens
import uikit.compose.UIKit
import uikit.compose.components.Header
import uikit.compose.components.SecondaryButton
import uikit.compose.components.TextHeader

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
        Header(
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

        TextHeader(
            title = stringResource(id = Localization.dapp_safe_modal_title),
            description = stringResource(id = Localization.dapp_safe_modal_subtitle),
        )

        Spacer(modifier = Modifier.height(Dimens.offsetExtraSmall))

        SecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSafeClick,
            text = stringResource(id = Localization.dapp_safe_model_button)
        )

        SecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClose,
            text = stringResource(id = Localization.close)
        )

        Spacer(modifier = Modifier.height(Dimens.offsetExtraSmall))
    }
}

@Preview
@Composable
private fun DAppSafeComposablePreview() {
    UIKit(theme = AppTheme.BLUE) {
        DAppSafeComposable(
            onSafeClick = {},
            onClose = {}
        )
    }
}