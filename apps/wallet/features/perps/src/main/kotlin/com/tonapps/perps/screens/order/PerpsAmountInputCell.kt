package com.tonapps.perps.screens.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.perps.data.PERPS_QUOTE_INPUT_SCALE
import com.tonapps.perps.data.limitFractionDigits
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.uikit.icon.UIKitIcon
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import ui.components.moon.MoonItemIcon
import ui.components.moon.cell.MoonBundleCell
import ui.painterResource
import ui.theme.UIKit

@Composable
internal fun PerpsAmountInputCell(
    amount: String,
    pillText: String?,
    minHeight: Dp = Dp.Unspecified,
    assetSymbol: String? = null,
    maxFractionDigits: Int = PERPS_QUOTE_INPUT_SCALE,
    onAmountChange: (String) -> Unit,
    onSwap: (() -> Unit)?,
    onDone: () -> Unit,
) {
    MoonBundleCell {
        Column(
            Modifier
                .defaultMinSize(minHeight = minHeight)
                .padding(remember { PaddingValues(vertical = 28.dp, horizontal = 16.dp) })
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val focusRequester = remember { FocusRequester() }
                val scrollState = rememberScrollState()
                val style = remember { TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp) }

                var field by remember { mutableStateOf(TextFieldValue(amount, TextRange(amount.length))) }

                LaunchedEffect(amount) {
                    if (amount != field.text) {
                        field = TextFieldValue(amount, TextRange(amount.length))
                    }
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                if (assetSymbol == null) {
                    Text(
                        text = "$",
                        style = style,
                        color = UIKit.colorScheme.text.secondary,
                    )
                }

                MoonEditText(
                    paddingValues = PaddingValues(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .weight(1f, fill = false)
                        .horizontalScroll(scrollState)
                        .width(IntrinsicSize.Min)
                        .defaultMinSize(minWidth = 24.dp),
                    value = field,
                    onValueChange = { changed ->
                        val sanitized = limitFractionDigits(sanitizeAmount(changed.text), maxFractionDigits)
                        if (sanitized != field.text) {
                            onAmountChange(sanitized)
                        }
                        val caret = (changed.selection.end + sanitized.length - changed.text.length)
                            .coerceIn(0, sanitized.length)
                        field = if (sanitized == changed.text) {
                            changed
                        } else {
                            TextFieldValue(sanitized, TextRange(caret))
                        }
                    },
                    textStyle = style,
                    keyboardActions = remember { KeyboardActions(onDone = { onDone() }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text(text = "0", style = style, color = UIKit.colorScheme.text.primary) },
                )

                if (assetSymbol != null) {
                    Text(
                        modifier = Modifier.padding(bottom = 4.dp),
                        text = assetSymbol,
                        style = UIKit.typography.num2,
                        color = UIKit.colorScheme.text.secondary,
                    )
                }

                DisposableEffect(Unit) {
                    focusRequester.requestFocus()
                    onDispose { focusRequester.freeFocus() }
                }
            }

            if (pillText != null) {
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(
                            if (onSwap != null) {
                                Modifier.clickable(onClick = onSwap)
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = pillText,
                        color = UIKit.colorScheme.text.secondary,
                        style = UIKit.typography.body1,
                    )
                    if (onSwap != null) {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_swap_horizontal_outline_28),
                            size = 16.dp,
                            color = UIKit.colorScheme.icon.secondary,
                        )
                    }
                }
            }
        }
    }
}
