package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.portfolio.screens.raffle.raffleBadgeIconRes
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Plurals
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import ui.components.moon.MoonItemIcon
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.TextCell
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun RaffleRow(
    raffle: RaffleEntity,
    onClick: () -> Unit,
) {
    RaffleRowInternal(
        title = raffle.compactBanner.let { banner ->
            val tickets = raffle.progress?.ticketsTotal ?: 0
            if (tickets > 0) banner.activeTitle else banner.defaultTitle
        },
        iconId = raffle.compactBanner.iconId,
        tickets = raffle.progress?.ticketsTotal ?: 0,
        onClick = onClick,
    )
}

@Composable
private fun RaffleRowInternal(
    title: String,
    iconId: String?,
    tickets: Int,
    onClick: () -> Unit,
) {
    MoonBundleCell(
        margins = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        TextCell(
            title = title,
            image = {
                MoonItemIcon(
                    painter = painterResource(raffleBadgeIconRes(iconId)),
                    color = UIKit.colorScheme.accent.blue,
                )
            },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tickets > 0) {
                        Text(
                            text = pluralStringResource(Plurals.raffle_tickets, tickets, tickets),
                            style = UIKit.typography.label3,
                            color = UIKit.colorScheme.accent.blue,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(UIKit.colorScheme.accent.blue.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_chevron_right_16),
                        size = 16.dp,
                    )
                }
            },
            onClick = onClick,
        )
    }
}

@Preview
@Composable
private fun RaffleRowPreview() {
    ThemedPreview(isDarkOnly = true) {
        RaffleRowInternal(
            title = "Mystery Raffle",
            iconId = "ticket",
            tickets = 123,
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun RaffleRowNotJoinedPreview() {
    ThemedPreview(isDarkOnly = true) {
        RaffleRowInternal(
            title = "Join Mystery Raffle",
            iconId = "ticket",
            tickets = 0,
            onClick = {},
        )
    }
}
