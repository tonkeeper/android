package com.tonapps.tonkeeper.ui.screen.events.compose.details.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.wallet.localization.Localization
import ui.components.button.TKButton
import ui.theme.ButtonColorsOrange
import ui.theme.ButtonColorsSecondary
import ui.theme.ButtonSizeSmall

@Composable
fun TxSpamButtons(onClick: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TKButton(
            text = stringResource(Localization.report_spam),
            size = ButtonSizeSmall,
            buttonColors = ButtonColorsOrange,
            onClick = { onClick(true) },
        )

        TKButton(
            text = stringResource(Localization.not_spam),
            size = ButtonSizeSmall,
            buttonColors = ButtonColorsSecondary,
            onClick = { onClick(false) },
        )
    }
}