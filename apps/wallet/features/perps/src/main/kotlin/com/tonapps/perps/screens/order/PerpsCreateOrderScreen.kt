package com.tonapps.perps.screens.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.perps.data.formatPlainAmount
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.RStr
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.PropertyCellStyle
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.utils.uppercased

private const val MARKET_PRICE = "$66 141.70"
private const val DEFAULT_LEVERAGE = 40
private const val BALANCE_USD = 712.56

@Composable
fun PerpsCreateOrderScreen(
    side: PerpsOrderSide,
    symbol: String,
    onClose: () -> Unit,
    onReview: (marginUsd: Double) -> Unit,
    onSetLimitPrice: () -> Unit = {},
    onTopUp: () -> Unit = {},
) {
    var orderType by rememberSaveable { mutableStateOf(PerpsOrderType.Market) }
    var amount by rememberSaveable { mutableStateOf("") }
    var inputInSize by rememberSaveable { mutableStateOf(false) }
    var leverage by rememberSaveable { mutableStateOf(DEFAULT_LEVERAGE) }
    var showOrderType by remember { mutableStateOf(false) }
    var showLeverage by remember { mutableStateOf(false) }
    var showAutoClose by remember { mutableStateOf(false) }

    val entered = amount.toDoubleOrNull() ?: 0.0
    val marginUsd = if (inputInSize) entered / leverage else entered
    val sizeUsd = marginUsd * leverage
    val insufficient = marginUsd > BALANCE_USD
    val canReview = marginUsd > 0.0 && !insufficient

    val pillText = if (inputInSize) {
        "Margin: ${formatUsd(marginUsd)}"
    } else {
        "Size: ${formatUsd(sizeUsd)}"
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    MoonScaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        topBar = {
            MoonTopAppBar(
                title = { MoonTopAppBarTitle(text = "${side.label} $symbol") },
                onTitleClick = {
                    keyboardController?.hide()
                    showOrderType = true
                },
                subtitle = {
                    MoonTopAppBarSubtitle(
                        text = "Price $MARKET_PRICE · ${orderType.name}",
                    )
                    Spacer(Modifier.width(4.dp))
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_switch_16),
                        size = 12.dp,
                        color = UIKit.colorScheme.icon.secondary,
                    )
                },
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onClose,
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection())
            ) {
                Spacer(Modifier.height(8.dp))

                OrderAmountInputCell(
                    amount = amount,
                    pillText = pillText,
                    onAmountChange = { amount = sanitizeAmount(it) },
                    onSwap = {
                        amount = when {
                            entered <= 0.0 -> ""
                            inputInSize -> formatPlainAmount(entered / leverage)
                            else -> formatPlainAmount(entered * leverage)
                        }
                        inputInSize = !inputInSize
                    },
                    onDone = {
                        if (canReview) {
                            onReview(marginUsd)
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MoonBadgeButton(
                        content = { MoonSmallItemTitle(stringResource(RStr.max).uppercased()) },
                        onClick = {
                            amount = if (inputInSize) {
                                formatPlainAmount(BALANCE_USD * leverage)
                            } else {
                                formatPlainAmount(BALANCE_USD)
                            }
                        },
                    )

                    if (insufficient) {
                        MoonItemSubtitle(
                            text = "Insufficient balance",
                            color = UIKit.colorScheme.accent.red,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MoonItemSubtitle(
                                text = "Balance: ${formatUsd(BALANCE_USD)} · ",
                                color = UIKit.colorScheme.text.secondary,
                            )
                            Text(
                                modifier = Modifier.clickable(onClick = onTopUp),
                                text = "Deposit",
                                style = UIKit.typography.body2,
                                color = UIKit.colorScheme.text.accent,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                MoonBundleCell {
                    Column {
                        MoonPropertyBigCell(
                            title = "Leverage",
                            value = "${leverage}x",
                            valueDescription = null,
                            onClick = {
                                keyboardController?.hide()
                                showLeverage = true
                            },
                            valuePostfix = { MoonChevronRight() },
                        )

                        MoonItemDivider()

                        MoonPropertyBigCell(
                            title = { MoonLargeItemSubtitle(text = "Auto Close") }, // TODO Str
                            content = {
                                MoonItemTitle( // TODO state when we have value
                                    text = "Set",
                                    color = UIKit.colorScheme.text.accent
                                )
                            },
                            onClick = {
                                keyboardController?.hide()
                                showAutoClose = true
                            },
                            valuePostfix = { MoonChevronRight() },
                        )
                    }
                }
            }

            MoonBottomButtonCell(
                text = "Review",
                enabled = canReview,
            ) {
                onReview(marginUsd)
            }
        }
    }

    if (showOrderType) {
        PerpsOrderTypeDialog(
            selected = orderType,
            onSelect = { type ->
                orderType = type
                if (type == PerpsOrderType.Limit) {
                    onSetLimitPrice()
                }
            },
            onClose = { showOrderType = false },
        )
    }

    if (showLeverage) {
        PerpsLeverageDialog(
            initialLeverage = leverage,
            onApply = { leverage = it },
            onClose = { showLeverage = false },
        )
    }

    if (showAutoClose) {
        PerpsAutoCloseDialog(
            onSet = {},
            onClose = { showAutoClose = false },
        )
    }
}

@Composable
private fun accentValueStyle(): PropertyCellStyle = PropertyCellStyle(
    titleStyle = UIKit.typography.body2,
    titleColor = UIKit.colorScheme.text.secondary,
    valueStyle = UIKit.typography.label2,
    valueColor = UIKit.colorScheme.text.accent,
)

@Composable
private fun OrderAmountInputCell(
    amount: String,
    pillText: String,
    onAmountChange: (String) -> Unit,
    onSwap: () -> Unit,
    onDone: () -> Unit,
) {
    MoonBundleCell {
        Column(
            Modifier
                .padding(remember { PaddingValues(vertical = 28.dp, horizontal = 16.dp) })
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val focusRequester = remember { FocusRequester() }
                val scrollState = rememberScrollState()
                val style = remember { TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp) }

                LaunchedEffect(amount) {
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
                    value = amount,
                    onValueChange = onAmountChange,
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

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSwap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = pillText,
                    color = UIKit.colorScheme.text.secondary,
                    style = UIKit.typography.body1,
                )
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_swap_horizontal_outline_28),
                    size = 16.dp,
                    color = UIKit.colorScheme.icon.secondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PerpsCreateOrderScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsCreateOrderScreen(
            side = PerpsOrderSide.Long,
            symbol = "BTC",
            onClose = {},
            onReview = {},
        )
    }
}
