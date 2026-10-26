package com.tonapps.perps.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun PerpsEditPositionDialog(
    side: PerpsOrderSide,
    directions: Set<PerpsSizeDirection>,
    onSelect: (PerpsSizeDirection) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsEditPositionDialogBody(
            side = side,
            directions = directions,
            onSelect = { direction ->
                navigator.close()
                onSelect(direction)
            },
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsEditPositionDialogBody(
    side: PerpsOrderSide,
    directions: Set<PerpsSizeDirection>,
    onSelect: (PerpsSizeDirection) -> Unit,
    onClose: () -> Unit,
) {
    val sideText = stringResource(
        if (side == PerpsOrderSide.LONG) {
            Localization.perps_long
        } else {
            Localization.perps_short
        }
    )
    val showAdd = PerpsSizeDirection.ADD in directions
    val showReduce = PerpsSizeDirection.REDUCE in directions

    MoonTopAppBarSimple(
        title = stringResource(Localization.perps_edit_position_title),
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
    )

    MoonSurface {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            MoonBundleCell {
                Column {
                    if (showAdd) {
                        PerpsEditPositionRow(
                            iconRes = UIKitIcon.ic_plus_outline_28,
                            title = stringResource(Localization.perps_edit_add_title),
                            description = stringResource(
                                Localization.perps_edit_add_description,
                                sideText.lowercase(),
                            ),
                            onClick = { onSelect(PerpsSizeDirection.ADD) },
                        )
                    }
                    if (showAdd && showReduce) {
                        MoonItemDivider()
                    }
                    if (showReduce) {
                        PerpsEditPositionRow(
                            iconRes = UIKitIcon.ic_minus_outline_28,
                            title = stringResource(Localization.perps_edit_reduce_title),
                            description = stringResource(Localization.perps_edit_reduce_description),
                            onClick = { onSelect(PerpsSizeDirection.REDUCE) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PerpsEditPositionRow(
    iconRes: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    TextCell(
        title = title,
        subtitle = description,
        maxLinesSubtitle = 2,
        onClick = onClick,
        content = { MoonChevronRight() },
        minHeight = 76.dp,
        image = {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(color = UIKit.colorScheme.accent.blue, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    MoonItemIcon(
                        painter = painterResource(iconRes),
                        size = 16.dp,
                        color = Color.White,
                    )
                }
            }
        },
    )
}

@Preview(showSystemUi = true)
@Composable
private fun PerpsEditPositionDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsEditPositionDialogBody(
            side = PerpsOrderSide.LONG,
            directions = setOf(PerpsSizeDirection.ADD, PerpsSizeDirection.REDUCE),
            onSelect = {},
            onClose = {},
        )
    }
}
