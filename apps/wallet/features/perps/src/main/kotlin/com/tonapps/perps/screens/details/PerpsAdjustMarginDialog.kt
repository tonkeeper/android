package com.tonapps.perps.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
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
fun PerpsAdjustMarginDialog(
    onAddMargin: () -> Unit,
    onReduceMargin: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsAdjustMarginDialogBody(
            onAddMargin = {
                navigator.close()
                onAddMargin()
            },
            onReduceMargin = {
                navigator.close()
                onReduceMargin()
            },
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsAdjustMarginDialogBody(
    onAddMargin: () -> Unit,
    onReduceMargin: () -> Unit,
    onClose: () -> Unit,
) {
    MoonTopAppBarSimple(
        title = "Adjust margin",
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
                    MarginRow(
                        iconRes = UIKitIcon.ic_plus_outline_28,
                        title = "Add margin",
                        description = "Add margin to make your position safer and lower the risk of liquidation",
                        onClick = onAddMargin,
                    )
                    MoonItemDivider()
                    MarginRow(
                        iconRes = UIKitIcon.ic_minus_outline_28,
                        title = "Reduce margin",
                        description = "Pull back margin when your position looks strong",
                        onClick = onReduceMargin,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarginRow(
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
                modifier = Modifier
                    .size(44.dp)
                    .background(color = UIKit.colorScheme.accent.blue, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MoonItemIcon(
                    painter = painterResource(iconRes),
                    size = 28.dp,
                    color = Color.White,
                )
            }
        },
    )
}

@Preview(showSystemUi = true)
@Composable
private fun PerpsAdjustMarginDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAdjustMarginDialogBody(
            onAddMargin = {},
            onReduceMargin = {},
            onClose = {},
        )
    }
}
