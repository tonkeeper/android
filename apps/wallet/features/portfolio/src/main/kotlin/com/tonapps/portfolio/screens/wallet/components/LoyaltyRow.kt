package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.core.loyalty.LoyaltyOhmsBadge
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.cell.MoonBundleCell
import ui.painterResource
import ui.preview.ThemedPreview

private val LoyaltyRowMargins = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)

@Composable
fun LoyaltyRow(
    ohms: String?,
    hiddenBalance: Boolean,
    onClick: () -> Unit,
) {
    MoonBundleCell(
        margins = LoyaltyRowMargins,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonItemImage(
                painter = painterResource(UIKitIcon.ic_ohms_28),
                size = 28.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (ohms == null) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemTitle(
                        text = stringResource(Localization.loyalty_join_resistance),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    MoonLabel(
                        text = stringResource(Localization.badge_new),
                        colors = MoonLabelDefault.blue(),
                    )
                }
            } else {
                MoonItemTitle(
                    text = stringResource(Localization.loyalty_resistance),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                LoyaltyOhmsBadge(ohms = ohms, hiddenBalance = hiddenBalance)
            }
            Spacer(modifier = Modifier.width(8.dp))
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_chevron_right_16),
                size = 16.dp,
            )
        }
    }
}

@Preview
@Composable
private fun LoyaltyRowJoinPreview() {
    ThemedPreview {
        LoyaltyRow(ohms = null, hiddenBalance = false, onClick = {})
    }
}

@Preview
@Composable
private fun LoyaltyRowOhmsPreview() {
    ThemedPreview {
        LoyaltyRow(ohms = "4000", hiddenBalance = false, onClick = {})
    }
}

@Preview
@Composable
private fun LoyaltyRowHiddenOhmsPreview() {
    ThemedPreview {
        LoyaltyRow(ohms = "4000", hiddenBalance = true, onClick = {})
    }
}
