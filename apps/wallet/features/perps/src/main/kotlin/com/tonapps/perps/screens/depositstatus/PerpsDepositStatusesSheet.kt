package com.tonapps.perps.screens.depositstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.base.UIKitProgressIndicator
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.TextCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
internal fun PerpsDepositStatusesSheet(
    items: List<PerpsDepositListItemUi>,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = stringResource(Localization.perps_deposit_statuses_title),
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
        )
        MoonSurface {
            PerpsDepositStatusesList(items = items, onOpen = onOpen)
        }
    }
}

@Composable
private fun PerpsDepositStatusesList(
    items: List<PerpsDepositListItemUi>,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        items.forEachIndexed { index, item ->
            MoonBundleCell(
                position = defaultBundleType(items.size, index),
                onClick = { onOpen(item.depositId) },
            ) {
                PerpsDepositStatusesRow(item = item)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsDepositStatusesRow(item: PerpsDepositListItemUi) {
    val problem = when (item.state) {
        PerpsDepositListState.FAILED -> stringResource(Localization.failed)
        PerpsDepositListState.UNCONFIRMED -> stringResource(Localization.perps_deposit_status_unconfirmed)
        PerpsDepositListState.DEPOSITING,
        PerpsDepositListState.DEPOSITED -> null
    }
    TextCell(
        modifier = Modifier.padding(vertical = 8.dp),
        minHeight = 76.dp,
        verticalAlignment = Alignment.Top,
        image = { PerpsDepositStatusesIcon(state = item.state) },
        title = {
            Row(modifier = Modifier.fillMaxWidth()) {
                MoonItemTitle(
                    text = stringResource(
                        when (item.state) {
                            PerpsDepositListState.DEPOSITING -> Localization.history_perps_depositing
                            PerpsDepositListState.DEPOSITED -> Localization.history_perps_deposited
                            PerpsDepositListState.FAILED,
                            PerpsDepositListState.UNCONFIRMED -> Localization.perps_deposit
                        },
                    ),
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.amountFormat,
                    style = UIKit.typography.label1,
                    color = if (item.positive) {
                        UIKit.colorScheme.accent.green
                    } else {
                        UIKit.colorScheme.text.primary
                    },
                    maxLines = 1,
                )
            }
        },
        subtitle = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MoonItemSubtitle(
                        text = stringResource(Localization.perps_balance),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MoonItemSubtitle(text = item.dateFormat, maxLines = 1)
                }
                if (problem != null) {
                    MoonItemSubtitle(
                        text = problem,
                        maxLines = 1,
                        color = UIKit.colorScheme.accent.orange,
                    )
                }
            }
        },
    )
}

@Composable
private fun PerpsDepositStatusesIcon(state: PerpsDepositListState) {
    MoonCutBadgedBox(
        badge = if (state == PerpsDepositListState.DEPOSITING) {
            { UIKitProgressIndicator() }
        } else {
            null
        },
        direction = BadgeDirection.StartTop,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(UIKit.colorScheme.background.contentTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    when (state) {
                        PerpsDepositListState.FAILED,
                        PerpsDepositListState.UNCONFIRMED -> UIKitIcon.ic_exclamationmark_circle_28
                        PerpsDepositListState.DEPOSITING,
                        PerpsDepositListState.DEPOSITED -> UIKitIcon.ic_tray_arrow_down_28
                    },
                ),
                contentDescription = null,
                tint = UIKit.colorScheme.icon.secondary,
            )
        }
    }
}

@Preview
@Composable
private fun PerpsDepositStatusesPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsDepositStatusesList(
            items = listOf(
                PerpsDepositListItemUi("1", PerpsDepositListState.DEPOSITING, "$99.80", false, "4 Feb, 16:00"),
                PerpsDepositListItemUi("2", PerpsDepositListState.FAILED, "$99.80", false, "3 Feb, 16:00"),
                PerpsDepositListItemUi("3", PerpsDepositListState.DEPOSITED, "+$99.80", true, "3 Feb, 16:00"),
            ),
            onOpen = {},
        )
    }
}
