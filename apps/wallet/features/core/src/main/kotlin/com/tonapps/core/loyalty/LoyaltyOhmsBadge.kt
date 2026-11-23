package com.tonapps.core.loyalty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun LoyaltyOhmsBadge(
    ohms: String,
    hiddenBalance: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            stringResource(Localization.loyalty_ohms, ohms)
        },
        style = UIKit.typography.label2,
        color = UIKit.colorScheme.accent.orange,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(UIKit.colorScheme.accent.orange.copy(alpha = 0.12f))
            .heightIn(min = 28.dp)
            .wrapContentHeight()
            .padding(horizontal = 12.dp),
    )
}

@Preview
@Composable
private fun LoyaltyOhmsBadgePreview() {
    ThemedPreview {
        LoyaltyOhmsBadge(ohms = "123", hiddenBalance = false)
    }
}

@Preview
@Composable
private fun LoyaltyOhmsBadgeHiddenPreview() {
    ThemedPreview {
        LoyaltyOhmsBadge(ohms = "123", hiddenBalance = true)
    }
}
