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

private const val HIGH_FEE_SHARE_BPS = 1_500
private const val BPS_PER_PERCENT = 100

internal fun isHighFeeShare(feeShareBps: Int): Boolean = feeShareBps >= HIGH_FEE_SHARE_BPS

@Composable
internal fun PerpetualHighFeeNote(feeShareBps: Int, modifier: Modifier = Modifier) {
    val percent = (feeShareBps + BPS_PER_PERCENT / 2) / BPS_PER_PERCENT
    Text(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        text = stringResource(Localization.perps_funding_high_fee, percent),
        style = UIKit.typography.body2,
        color = UIKit.colorScheme.accent.orange,
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun PerpetualHighFeeNotePreview() {
    ThemedPreview {
        PerpetualHighFeeNote(feeShareBps = 3_650)
    }
}
