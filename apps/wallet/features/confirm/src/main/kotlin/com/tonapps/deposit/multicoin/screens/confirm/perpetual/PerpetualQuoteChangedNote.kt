package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.wallet.localization.Localization
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
internal fun PerpetualQuoteChangedNote(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        text = stringResource(Localization.perps_deposit_quote_changed),
        style = UIKit.typography.body2,
        color = UIKit.colorScheme.accent.red,
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun PerpetualQuoteChangedNotePreview() {
    ThemedPreview {
        PerpetualQuoteChangedNote()
    }
}
