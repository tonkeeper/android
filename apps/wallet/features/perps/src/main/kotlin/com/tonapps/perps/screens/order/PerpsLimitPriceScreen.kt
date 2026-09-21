package com.tonapps.perps.screens.order

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.perps.data.formatPlainAmount
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.uikit.icon.UIKitIcon
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonOutlineChip
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.outlineStoke
import java.text.DecimalFormat

private const val MARK_PRICE = 66141.70
private const val MIN_PRICE = MARK_PRICE * 0.2
private const val MAX_PRICE = MARK_PRICE * 5.0

@Composable
fun PerpsLimitPriceScreen(
    onClose: () -> Unit,
    onBack: () -> Unit,
    onSet: (price: Double) -> Unit,
) {
    var price by rememberSaveable { mutableStateOf("") }

    val parsed = price.toDoubleOrNull() ?: 0.0
    val percent = if (parsed > 0.0) (parsed - MARK_PRICE) / MARK_PRICE * 100.0 else 0.0
    val error = when {
        parsed > MAX_PRICE -> "Max price ${formatUsdPrice(MAX_PRICE)}"
        parsed > 0.0 && parsed < MIN_PRICE -> "Min price ${formatUsdPrice(MIN_PRICE)}"
        else -> null
    }
    val canSet = parsed > 0.0 && error == null

    MoonScaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        title = "Set Limit Price",
        subtitle = "Price ${formatMarkPrice()}",
        onClose = onClose,
        onBack = onBack,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection())
            ) {
                Spacer(Modifier.height(8.dp))
                
                LimitPriceInputCell(
                    price = price,
                    percentText = formatPercent(percent),
                    error = error,
                    onPriceChange = { price = sanitizeAmount(it) },
                    onDone = {
                        if (canSet) {
                            onSet(parsed)
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickButton("MID") { price = formatPlainAmount(MARK_PRICE) }
                    QuickButton("-1%") { price = formatPlainAmount(MARK_PRICE * 0.99) }
                    QuickButton("-2%") { price = formatPlainAmount(MARK_PRICE * 0.98) }
                    QuickButton("-5%") { price = formatPlainAmount(MARK_PRICE * 0.95) }
                }
            }

            MoonBottomButtonCell(
                text = "Set",
                enabled = canSet,
            ) {
                onSet(parsed)
            }
        }
    }
}

@Composable
private fun RowScope.QuickButton(
    text: String,
    onClick: () -> Unit,
) {
    MoonOutlineChip(text = text, modifier = Modifier.weight(1f), isSelected = false, onClick = onClick)
}

@Composable
private fun LimitPriceInputCell(
    price: String,
    percentText: String,
    error: String?,
    onPriceChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    MoonBundleCell {
        Column(
            Modifier
                .padding(remember { PaddingValues(top = 29.dp, bottom = 0.dp, start = 16.dp, end = 16.dp) })
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val focusRequester = remember { FocusRequester() }
                val scrollState = rememberScrollState()
                val style = remember { TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp) }

                LaunchedEffect(price) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Text(
                    text = "$",
                    style = style,
                    color = UIKit.colorScheme.text.secondary,
                )

                MoonEditText(
                    paddingValues = PaddingValues(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .weight(1f, fill = false)
                        .horizontalScroll(scrollState)
                        .width(IntrinsicSize.Min)
                        .defaultMinSize(minWidth = 24.dp),
                    value = price,
                    onValueChange = onPriceChange,
                    textStyle = style,
                    keyboardActions = remember { KeyboardActions(onDone = { onDone() }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text(text = "0", style = style, color = UIKit.colorScheme.text.tertiary) },
                )

                DisposableEffect(Unit) {
                    focusRequester.requestFocus()
                    onDispose { focusRequester.freeFocus() }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .border(border = outlineStoke(), shape = CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = percentText,
                    color = UIKit.colorScheme.text.secondary,
                    style = UIKit.typography.body1,
                )
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                    size = 16.dp,
                    color = UIKit.colorScheme.icon.secondary,
                )
            }

            Spacer(Modifier.height(8.dp))

            MoonItemSubtitle(
                modifier = Modifier
                    .alpha(if (error != null) 1f else 0f)
                    .padding(horizontal = 6.dp),
                text = error ?: "",
                color = UIKit.colorScheme.accent.red,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatMarkPrice(): String {
    val symbols = java.text.DecimalFormatSymbols().apply {
        groupingSeparator = ' '
        decimalSeparator = '.'
    }
    return "$" + DecimalFormat("#,##0.00", symbols).format(MARK_PRICE)
}

private fun formatPercent(percent: Double): String {
    val rounded = DecimalFormat("#.##").format(percent)
    val prefix = if (percent > 0.0) "+" else ""
    return "$prefix$rounded%"
}

@Preview
@Composable
private fun PerpsLimitPriceScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsLimitPriceScreen(
            onClose = {},
            onBack = {},
            onSet = {},
        )
    }
}
