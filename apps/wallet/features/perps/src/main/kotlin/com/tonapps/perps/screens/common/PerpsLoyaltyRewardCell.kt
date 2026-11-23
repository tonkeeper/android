package com.tonapps.perps.screens.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.painterResource
import ui.preview.ThemedPreview

@Composable
fun PerpsLoyaltyRewardCell(
    amount: String,
    hiddenBalance: Boolean,
    onClick: () -> Unit,
) {
    MoonPropertyBigCell(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonLargeItemSubtitle(text = stringResource(Localization.loyalty_resistance_reward))
                Spacer(Modifier.width(6.dp))
                MoonItemIcon(painter = painterResource(UIKitIcon.ic_information_circle_12))
            }
        },
        content = {
            if (hiddenBalance) {
                MoonItemTitle(text = HIDDEN_BALANCE)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoonItemTitle(text = stringResource(Localization.loyalty_reward_value, amount))
                    Spacer(Modifier.width(4.dp))
                    MoonItemImage(
                        painter = painterResource(UIKitIcon.ic_ohms_28),
                        size = 16.dp,
                    )
                }
            }
        },
        onClick = onClick,
    )
}

@Preview
@Composable
private fun PerpsLoyaltyRewardCellPreview() {
    ThemedPreview {
        MoonBundleCell {
            PerpsLoyaltyRewardCell(amount = "50", hiddenBalance = false, onClick = {})
        }
    }
}
