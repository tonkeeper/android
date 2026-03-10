package com.tonapps.tonkeeper.ui.screen.events.compose.details.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.wallet.localization.Localization
import ui.theme.UIKit

@Composable
fun TxSpamBadge() {
    Text(
        modifier = Modifier
            .background(
                color = UIKit.colorScheme.accent.orange,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        text = stringResource(Localization.spam).uppercase(),
        style = UIKit.typography.label2,
        color = UIKit.colorScheme.text.primary
    )
}