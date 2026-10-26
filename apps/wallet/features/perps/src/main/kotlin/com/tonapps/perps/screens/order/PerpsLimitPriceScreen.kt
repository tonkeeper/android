package com.tonapps.perps.screens.order

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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.formatOffsetPercent
import com.tonapps.perps.data.formatUsd
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonOutlineChip
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.theme.UIKit
import ui.theme.outlineStoke
import java.math.BigDecimal

@Composable
fun PerpsLimitPriceScreen(
    feature: PerpsLimitPriceFeature,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onSet: (price: BigDecimal) -> Unit,
) {
    val referencePrice by feature.referencePrice.collectAsStateWorkaround()
    val input by feature.input.collectAsStateWorkaround()
    val limitPrice by feature.limitPrice.collectAsStateWorkaround()
    val chipText by feature.chipText.collectAsStateWorkaround()
    val warning by feature.warning.collectAsStateWorkaround()
    val canSet by feature.canSet.collectAsStateWorkaround()

    val referenceText = referencePrice
        ?.let { formatUsd(it) }
        ?: PERPS_EMPTY_VALUE
    val submit: () -> Unit = {
        limitPrice?.let(onSet)
    }

    MoonScaffold(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.ime.union(WindowInsets.navigationBars).union(WindowInsets.statusBars),
        ),
        title = stringResource(Localization.perps_limit_price_title),
        subtitle = stringResource(
            Localization.perps_order_price_market,
            stringResource(Localization.perps_order_current_price, referenceText),
            stringResource(Localization.perps_order_type_limit),
        ),
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
                    value = input.text,
                    inPercent = input.inPercent,
                    chipText = chipText,
                    warning = warning,
                    onValueChange = { feature.sendAction(PerpsLimitPriceAction.SetInput(it)) },
                    onToggleMode = { feature.sendAction(PerpsLimitPriceAction.ToggleInputMode) },
                    onDone = {
                        if (canSet) {
                            submit()
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
                    feature.quickFills.forEach { percent ->
                        MoonOutlineChip(
                            text = if (percent.signum() == 0) {
                                stringResource(Localization.perps_limit_quick_mid)
                            } else {
                                formatOffsetPercent(percent)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .alpha(
                                    if (referencePrice != null) {
                                        1f
                                    } else {
                                        DISABLED_ALPHA
                                    }
                                ),
                            isSelected = percent == input.quickFill,
                            onClick = {
                                if (referencePrice != null) {
                                    feature.sendAction(PerpsLimitPriceAction.SelectQuickFill(percent))
                                }
                            },
                        )
                    }
                }
            }

            MoonBottomButtonCell(
                text = stringResource(Localization.perps_limit_price_set),
                enabled = canSet,
            ) {
                submit()
            }
        }
    }
}

@Composable
private fun LimitPriceInputCell(
    value: String,
    inPercent: Boolean,
    chipText: String,
    warning: Int?,
    onValueChange: (String) -> Unit,
    onToggleMode: () -> Unit,
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

                LaunchedEffect(value) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                if (!inPercent) {
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
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = style,
                    keyboardActions = remember { KeyboardActions(onDone = { onDone() }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text(text = "0", style = style, color = UIKit.colorScheme.text.tertiary) },
                )

                if (inPercent) {
                    Text(
                        text = "%",
                        style = style,
                        color = UIKit.colorScheme.text.secondary,
                    )
                }

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
                    .clickable(onClick = onToggleMode)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = chipText,
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
                    .alpha(
                        if (warning != null) {
                            1f
                        } else {
                            0f
                        }
                    )
                    .padding(horizontal = 6.dp),
                text = warning?.let { stringResource(it) } ?: "",
                color = UIKit.colorScheme.accent.red,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

private const val DISABLED_ALPHA = 0.4f
