package com.tonapps.perps.screens.deposit

import androidx.compose.foundation.border
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.perps.data.PerpsPaymentMethod
import com.tonapps.perps.data.formatPlainAmount
import com.tonapps.perps.data.formatTokenAmount
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.uikit.icon.UIKitIcon
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleCellContent
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.outlineStoke

@Composable
fun PerpsDepositScreen(
    onClose: () -> Unit,
    onReview: (method: PerpsPaymentMethod, cryptoAmount: Double) -> Unit,
    methods: List<PerpsPaymentMethod> = samplePerpsPaymentMethods,
    onTopUp: () -> Unit = {},
) {
    var selectedId by rememberSaveable { mutableStateOf(methods.first().id) }
    var amount by rememberSaveable { mutableStateOf("") }
    var inputInUsd by rememberSaveable { mutableStateOf(false) }
    var showMethods by remember { mutableStateOf(false) }

    val method = methods.firstOrNull { it.id == selectedId } ?: methods.first()

    val parsed = amount.toDoubleOrNull() ?: 0.0
    val cryptoAmount = if (inputInUsd) parsed / method.usdPrice else parsed
    val fiatAmount = cryptoAmount * method.usdPrice
    val insufficient = cryptoAmount > method.balance
    val canReview = cryptoAmount > 0.0 && !insufficient

    val inputCode = if (inputInUsd) "USD" else method.symbol
    val pillText = if (inputInUsd) {
        formatTokenAmount(cryptoAmount, method.symbol)
    } else {
        formatUsd(fiatAmount)
    }

    MoonScaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        title = "Deposit Perps balance",
        onClose = onClose,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection())
            ) {
                AmountInputCell(
                    amount = amount,
                    currencyCode = inputCode,
                    pillText = pillText,
                    onAmountChange = { amount = sanitizeAmount(it) },
                    onSwap = {
                        amount = when {
                            parsed <= 0.0 -> ""
                            inputInUsd -> formatPlainAmount(parsed / method.usdPrice)
                            else -> formatPlainAmount(parsed * method.usdPrice)
                        }
                        inputInUsd = !inputInUsd
                    },
                    onDone = {
                        if (canReview) {
                            onReview(method, cryptoAmount)
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
                        content = { MoonSmallItemTitle("MAX") },
                        onClick = {
                            amount = if (inputInUsd) {
                                formatPlainAmount(method.balance * method.usdPrice)
                            } else {
                                formatPlainAmount(method.balance)
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
                                text = "Balance: ${formatTokenAmount(method.balance, method.symbol)} · ",
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

                val keyboardController = LocalSoftwareKeyboardController.current
                PaymentMethodCell(
                    method = method,
                    onClick = {
                        keyboardController?.hide()
                        showMethods = true
                    },
                )
            }

            MoonBottomButtonCell(
                text = "Review",
                enabled = canReview,
            ) {
                onReview(method, cryptoAmount)
            }
        }
    }

    if (showMethods) {
        PerpsPaymentMethodDialog(
            methods = methods,
            selectedId = selectedId,
            onSelect = { selectedId = it },
            onClose = { showMethods = false },
        )
    }
}

@Composable
private fun AmountInputCell(
    amount: String,
    currencyCode: String,
    pillText: String,
    onAmountChange: (String) -> Unit,
    onSwap: () -> Unit,
    onDone: () -> Unit,
) {
    MoonBundleCell {
        Column(
            Modifier
                .padding(remember { PaddingValues(top = 39.dp, bottom = 6.dp, start = 16.dp, end = 16.dp) })
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

                LaunchedEffect(amount) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

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

                Text(
                    modifier = Modifier.padding(bottom = 4.dp),
                    text = currencyCode,
                    style = UIKit.typography.num2,
                    color = UIKit.colorScheme.text.secondary,
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
                    .clickable(onClick = onSwap)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = pillText,
                    color = UIKit.colorScheme.text.secondary,
                    style = UIKit.typography.body1,
                )
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_switch_16),
                    size = 16.dp,
                    color = UIKit.colorScheme.icon.secondary,
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodCell(
    method: PerpsPaymentMethod,
    onClick: () -> Unit,
) {
    MoonBundleCell(contentPadding = MoonBundleCellContent.Default) {
        TextCell(
            title = method.symbol,
            subtitle = "${formatTokenAmount(method.balance, method.symbol)} · ${formatUsd(method.balance * method.usdPrice)}",
            tags = { PerpsFeeBadge(method) },
            onClick = onClick,
            content = { MoonItemIcon(painterResource(UIKitIcon.ic_switch_16)) },
            minHeight = 76.dp,
            image = {
                MoonItemImage(
                    painter = painterResource(method.iconRes),
                    size = 44.dp,
                )
            },
        )
    }
}

@Composable
internal fun PerpsFeeBadge(method: PerpsPaymentMethod) {
    MoonLabel(
        text = method.feeLabel,
        colors = if (method.zeroFee) MoonLabelDefault.success() else MoonLabelDefault.blue(),
    )
}

internal val samplePerpsPaymentMethods = listOf(
    PerpsPaymentMethod(
        id = "usdt",
        symbol = "USDT",
        iconRes = UIKitIcon.ic_usdt,
        feeLabel = "0% fee",
        zeroFee = true,
        balance = 3906.0,
        usdPrice = 0.999,
    ),
    PerpsPaymentMethod(
        id = "ton",
        symbol = "TON",
        iconRes = UIKitIcon.ic_ton,
        feeLabel = "0.9% fee",
        zeroFee = false,
        balance = 2345.0,
        usdPrice = 1.474,
    ),
)

@Preview
@Composable
private fun PerpsDepositScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsDepositScreen(
            onClose = {},
            onReview = { _, _ -> },
        )
    }
}
