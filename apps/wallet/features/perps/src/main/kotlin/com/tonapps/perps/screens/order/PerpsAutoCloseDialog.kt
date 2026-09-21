package com.tonapps.perps.screens.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.formatPlainAmount
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonOutlineChip
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonTextFieldCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview

private const val MARK_PRICE = 66141.70

@Composable
fun PerpsAutoCloseDialog(
    onSet: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsAutoCloseDialogBody(
            onSet = {
                navigator.close()
                onSet()
            },
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsAutoCloseDialogBody(
    onSet: () -> Unit,
    onClose: () -> Unit,
) {
    var profitPercent by rememberSaveable { mutableStateOf("") }
    var takeProfitPrice by rememberSaveable { mutableStateOf("") }
    var lossPercent by rememberSaveable { mutableStateOf("") }
    var stopLossPrice by rememberSaveable { mutableStateOf("") }

    val canSet = profitPercent.isNotEmpty() || takeProfitPrice.isNotEmpty() ||
            lossPercent.isNotEmpty() || stopLossPrice.isNotEmpty()

    MoonTopAppBarSimple(
        title = "Auto close",
        subtitle = "Price ${formatUsdPrice(MARK_PRICE)}",
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
    )

    MoonSurface {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            MoonBundleTitleCell("Take profit when")
            AmountFieldsRow(
                percent = profitPercent,
                onPercentChange = { profitPercent = sanitizeAmount(it) },
                percentHint = "+ Profit %",
                price = takeProfitPrice,
                onPriceChange = { takeProfitPrice = sanitizeAmount(it) },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 20, 50, 100).forEach { percent ->
                    PercentChip(text = "+$percent%") {
                        profitPercent = percent.toString()
                        takeProfitPrice = formatPlainAmount(MARK_PRICE * (1.0 + percent / 100.0))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            MoonBundleTitleCell("Stop loss when")
            AmountFieldsRow(
                percent = lossPercent,
                onPercentChange = { lossPercent = sanitizeAmount(it) },
                percentHint = "− Loss %",
                price = stopLossPrice,
                onPriceChange = { stopLossPrice = sanitizeAmount(it) },
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 25, 50).forEach { percent ->
                    PercentChip(text = "−$percent%") {
                        lossPercent = percent.toString()
                        stopLossPrice = formatPlainAmount(MARK_PRICE * (1.0 - percent / 100.0))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            MoonBottomButtonCell(
                text = "Set",
                enabled = canSet,
                contentPadding = remember { PaddingValues(0.dp) },
                onClick = onSet,
            )
        }
    }
}


@Composable
private fun AmountFieldsRow(
    percent: String,
    onPercentChange: (String) -> Unit,
    percentHint: String,
    price: String,
    onPriceChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MoonTextFieldCell(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            value = percent,
            onValueChange = onPercentChange,
            hint = percentHint,
            singleLine = true,
            disableClearButton = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        MoonTextFieldCell(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            value = price,
            onValueChange = onPriceChange,
            hint = "$ Price",
            singleLine = true,
            disableClearButton = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

@Composable
private fun RowScope.PercentChip(
    text: String,
    onClick: () -> Unit,
) {
    MoonOutlineChip(text = text, modifier = Modifier.weight(1f), isSelected = false, onClick = onClick)
}

@Preview
@Composable
private fun PerpsAutoCloseDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAutoCloseDialogBody(
            onSet = {},
            onClose = {},
        )
    }
}
