package com.tonapps.perps.screens.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit
import java.math.BigDecimal

@Composable
fun PerpsLimitOrderActionsDialog(
    order: PerpsLimitOrder,
    priceDecimals: Int,
    editable: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsLimitOrderActionsBody(
            order = order,
            priceDecimals = priceDecimals,
            editable = editable,
            onEdit = {
                navigator.close()
                onEdit()
            },
            onCancel = {
                navigator.close()
                onCancel()
            },
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsLimitOrderActionsBody(
    order: PerpsLimitOrder,
    priceDecimals: Int,
    editable: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    MoonTopAppBarSimple(
        title = stringResource(Localization.perps_order_type_limit),
        subtitle = stringResource(
            Localization.perps_order_price,
            order.limitPrice?.let { formatUsdPrice(it.toDouble(), priceDecimals) } ?: PERPS_EMPTY_VALUE,
        ),
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
                    MoonPropertyBigCell(
                        title = {
                            MoonLargeItemSubtitle(
                                text = stringResource(Localization.edit),
                                color = if (editable) {
                                    UIKit.colorScheme.text.primary
                                } else {
                                    UIKit.colorScheme.text.tertiary
                                },
                            )
                        },
                        content = {},
                        contentDescription = if (editable) {
                            null
                        } else {
                            { MoonItemTitle(text = stringResource(Localization.perps_limit_order_too_small)) }
                        },
                        onClick = if (editable) {
                            onEdit
                        } else {
                            null
                        },
                    )
                    MoonItemDivider()
                    MoonPropertyBigCell(
                        title = {
                            MoonLargeItemSubtitle(
                                text = stringResource(Localization.perps_limit_order_cancel),
                                color = UIKit.colorScheme.accent.red,
                            )
                        },
                        content = {},
                        onClick = onCancel,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PerpsLimitOrderActionsDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsLimitOrderActionsBody(
            order = PerpsLimitOrder(
                orderIndex = 1L,
                clientOrderIndex = null,
                side = PerpsPositionSide.LONG,
                remainingBase = BigDecimal("0.0038"),
                limitPrice = BigDecimal("64200"),
            ),
            priceDecimals = 2,
            editable = true,
            onEdit = {},
            onCancel = {},
            onClose = {},
        )
    }
}
