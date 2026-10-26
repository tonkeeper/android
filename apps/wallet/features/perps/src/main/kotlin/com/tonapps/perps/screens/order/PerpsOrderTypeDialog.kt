package com.tonapps.perps.screens.order

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.TextCheckCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun PerpsOrderTypeDialog(
    selected: PerpsOrderType,
    onSelect: (PerpsOrderType) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsOrderTypeDialogBody(
            selected = selected,
            onSelect = { type ->
                navigator.close()
                onSelect(type)
            },
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsOrderTypeDialogBody(
    selected: PerpsOrderType,
    onSelect: (PerpsOrderType) -> Unit,
    onClose: () -> Unit,
) {
    MoonTopAppBarSimple(
        title = stringResource(Localization.perps_order_type_title),
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
                    OrderTypeRow(
                        iconRes = UIKitIcon.ic_money_28,
                        iconBackground = UIKit.colorScheme.accent.blue,
                        title = stringResource(Localization.perps_order_type_market),
                        description = stringResource(Localization.perps_order_type_market_description),
                        isChecked = selected == PerpsOrderType.Market,
                        onClick = { onSelect(PerpsOrderType.Market) },
                    )
                    MoonItemDivider()
                    OrderTypeRow(
                        iconRes = UIKitIcon.ic_sale_badge_28,
                        iconBackground = UIKit.colorScheme.accent.blue,
                        title = stringResource(Localization.perps_order_type_limit),
                        description = stringResource(Localization.perps_order_type_limit_description),
                        isChecked = selected == PerpsOrderType.Limit,
                        onClick = { onSelect(PerpsOrderType.Limit) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OrderTypeRow(
    iconRes: Int,
    iconBackground: Color,
    title: String,
    description: String,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    TextCheckCell(
        title = title,
        subtitle = description,
        maxLinesSubtitle = 2,
        isChecked = isChecked,
        onCheckedChange = { onClick() },
        minHeight = 96.dp,
        image = {
            MoonItemIcon(
                painter = painterResource(iconRes),
                color = iconBackground,
            )
        },
    )
}

@Preview(showSystemUi = true)
@Composable
private fun PerpsOrderTypeDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsOrderTypeDialogBody(
            selected = PerpsOrderType.Market,
            onSelect = {},
            onClose = {},
        )
    }
}
