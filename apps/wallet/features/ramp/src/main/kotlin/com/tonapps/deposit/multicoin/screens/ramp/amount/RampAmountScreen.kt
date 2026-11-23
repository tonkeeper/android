package com.tonapps.deposit.multicoin.screens.ramp.amount

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tonapps.core.components.withLocaleDecimalSeparator
import com.tonapps.deposit.screens.provider.ProviderItem
import com.tonapps.deposit.screens.provider.SelectProviderDialog
import com.tonapps.deposit.utils.AmountError
import com.tonapps.deposit.utils.ProviderAmountState
import com.tonapps.deposit.utils.rememberProviderAmountState
import com.tonapps.icu.Coins
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import com.wallet.crypto.trustapp.common.ui.components.amountFieldScroll
import kotlinx.coroutines.flow.SharedFlow
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer
import ui.utils.uppercased

@Composable
fun RampAmountScreen(
    feature: RampAmountFeature,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
) {
    val state by feature.state.collectAsState()

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            if (event is RampAmountEvent.Continue) {
                onContinue(event.widgetUrl)
            }
        }
    }

    MoonScaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        title = stringResource(Localization.deposit_insert_amount),
        onClose = onClose,
        onBack = onBack,
    ) {
        when (val current = state) {
            is RampAmountState.Loading -> MoonLoaderCell()
            is RampAmountState.Data -> {
                DataContent(
                    state = current,
                    events = feature.events,
                    onAmountInput = feature::onAmountInput,
                    onToggleInputCurrency = feature::toggleInputCurrency,
                    onProviderSelected = feature::selectProvider,
                    onContinue = feature::continueWithProvider,
                )
            }
        }
    }
}

@Composable
private fun DataContent(
    state: RampAmountState.Data,
    events: SharedFlow<RampAmountEvent>,
    onAmountInput: (String) -> Unit,
    onToggleInputCurrency: (String) -> Unit,
    onProviderSelected: (String, Coins?) -> Unit,
    onContinue: () -> Unit,
) {
    val amountState = rememberProviderAmountState(provider = null)

    LaunchedEffect(events) {
        events.collect { event ->
            if (event is RampAmountEvent.UpdateAmount) {
                amountState.onTextChange(event.text)
            }
        }
    }

    LaunchedEffect(state.initialAmount) {
        val initial = state.initialAmount
        val amount = if (initial != null && amountState.text.isEmpty()) {
            val localizedInitial = initial.withLocaleDecimalSeparator()
            amountState.onTextChange(localizedInitial)
            localizedInitial
        } else {
            amountState.text
        }
        if (amount.isNotEmpty()) {
            onAmountInput(amount)
        }
    }

    val selected = state.selectedProvider ?: state.providers.firstOrNull()
    var showProviderDialog by remember { mutableStateOf(false) }

    val insufficientBalance = state.balanceCoins?.let {
        amountState.coins.isPositive && amountState.coins > it
    } ?: false

    val canContinue = state.canContinue
            && amountState.hasAmount
            && !insufficientBalance
            && state.isQuotedFor(amountState.coins)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            AmountInputCell(
                amountState = amountState,
                currencyCode = state.inputCurrencyCode,
                convertedAmount = state.convertedAmount,
                isLoading = state.isCalculating,
                onToggle = if (state.canToggleInput) {
                    { onToggleInputCurrency(amountState.text) }
                } else {
                    null
                },
                onAmountInput = onAmountInput,
                onDone = {
                    if (canContinue) {
                        onContinue()
                    }
                },
            )

            state.balanceCoins?.let { balance ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MoonBadgeButton(
                        content = { MoonSmallItemTitle(stringResource(Localization.max).uppercased()) },
                        onClick = {
                            val maxAmount = Coins.string(balance).withLocaleDecimalSeparator()
                            amountState.onTextChange(maxAmount)
                            onAmountInput(maxAmount)
                        },
                    )
                    if (insufficientBalance) {
                        MoonItemSubtitle(
                            text = stringResource(Localization.insufficient_balance),
                            color = UIKit.colorScheme.accent.red,
                        )
                    } else if (state.balanceFormatted != null) {
                        MoonItemSubtitle(
                            text = stringResource(Localization.available, state.balanceFormatted),
                            color = UIKit.colorScheme.text.secondary,
                        )
                    }
                }
            }

            if (state.balanceCoins == null) {
                Spacer(Modifier.height(24.dp))
            }

            if (state.isCalculating) {
                val shimmer by rememberShimmerPhase()
                ProviderCell(
                    modifier = Modifier.shimmer(shimmer),
                    provider = ProviderItem.EMPTY,
                    subtitle = "",
                )
            } else if (selected != null && (selected.quote != null || state.amountError != null)) {
                val keyboardController = LocalSoftwareKeyboardController.current
                val amountError = state.amountError
                ProviderCell(
                    provider = selected.info,
                    subtitle = if (amountError != null) {
                        amountLimitText(amountError, state.limitCurrencyCode)
                    } else {
                        selected.rate?.rateFormatted
                    },
                    isSubtitleWarning = amountError != null,
                    onClick = {
                        keyboardController?.hide()
                        showProviderDialog = true
                    },
                )
            } else if (state.providers.isEmpty()) {
                EmptyProviderCell()
            }
        }

        MoonBottomButtonCell(
            text = stringResource(Localization.continue_action),
            enabled = canContinue,
            loading = state.isContinuing,
        ) {
            onContinue()
        }
    }

    if (showProviderDialog) {
        SelectProviderDialog(
            providers = state.providers,
            selectedProviderId = state.selectedProviderId,
            currencyCode = state.limitCurrencyCode,
            onConfirm = onProviderSelected,
            onClose = { showProviderDialog = false },
            amount = state.requestAmount,
        )
    }
}

@Composable
private fun amountLimitText(error: AmountError, currencyCode: String): String {
    return when (error) {
        is AmountError.BelowMin -> stringResource(
            Localization.deposit_min_amount_with_currency,
            Coins.string(error.minAmount).withLocaleDecimalSeparator(),
            currencyCode,
        )
        is AmountError.AboveMax -> stringResource(
            Localization.deposit_max_amount_with_currency,
            Coins.string(error.maxAmount).withLocaleDecimalSeparator(),
            currencyCode,
        )
    }
}

@Composable
private fun AmountInputCell(
    amountState: ProviderAmountState,
    currencyCode: String,
    convertedAmount: String,
    isLoading: Boolean,
    onToggle: (() -> Unit)?,
    onAmountInput: (String) -> Unit,
    onDone: () -> Unit,
) {
    MoonBundleCell {
        Column(
            Modifier
                .fillMaxWidth()
                .height(188.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .height(77.dp)
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val focusRequester = remember { FocusRequester() }
                val scrollState = rememberScrollState()
                val style = remember { TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp) }

                LaunchedEffect(amountState.value.text) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                MoonEditText(
                    paddingValues = PaddingValues(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .weight(1f, fill = false)
                        .amountFieldScroll(scrollState),
                    value = amountState.value,
                    onValueChange = { newValue ->
                        amountState.onValueChange(newValue)
                        onAmountInput(amountState.value.text)
                    },
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

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(width = 1.5.dp, color = UIKit.colorScheme.background.contentTint, shape = CircleShape)
                    .run {
                        if (onToggle != null) {
                            clickable(onClick = onToggle)
                        } else {
                            this
                        }
                    }
                    .run {
                        if (isLoading) {
                            val shimmer by rememberShimmerPhase()
                            shimmer(shimmer)
                        } else {
                            this
                        }
                    }
                    .height(40.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = convertedAmount,
                    color = UIKit.colorScheme.text.secondary,
                    style = UIKit.typography.body1,
                )
                if (onToggle != null) {
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                        size = 16.dp,
                        color = UIKit.colorScheme.icon.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProviderCell() {
    MoonBundleCell {
        TextCell(
            title = stringResource(Localization.no_available_provider),
            titleColor = UIKit.colorScheme.text.secondary,
            subtitle = null,
            onClick = null,
            minHeight = 76.dp,
            image = {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_wallet_28),
                    color = UIKit.colorScheme.icon.secondary,
                )
            },
        )
    }
}

@Composable
private fun ProviderCell(
    provider: ProviderItem,
    subtitle: String?,
    modifier: Modifier = Modifier,
    isSubtitleWarning: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    MoonBundleCell {
        TextCell(
            modifier = modifier,
            title = provider.title,
            subtitle = subtitle,
            subtitleColor = if (isSubtitleWarning) {
                UIKit.colorScheme.accent.orange
            } else {
                UIKit.colorScheme.text.secondary
            },
            tags = {
                if (provider.isBest) {
                    MoonLabel(stringResource(Localization.best), colors = MoonLabelDefault.blue())
                }
            },
            onClick = onClick,
            content = { MoonItemIcon(painterResource(UIKitIcon.ic_switch_16)) },
            minHeight = 76.dp,
            image = {
                MoonItemImage(
                    shape = UIKit.shapes.large,
                    image = provider.imageUrl,
                    size = 44.dp,
                )
            },
        )
    }
}

@Preview
@Composable
private fun RampAmountBelowMinimumPreview() {
    ThemedPreview {
        Column {
            AmountInputCell(
                amountState = remember { ProviderAmountState().apply { onTextChange("1") } },
                currencyCode = "TON",
                convertedAmount = "1.17 USD",
                isLoading = false,
                onToggle = {},
                onAmountInput = {},
                onDone = {},
            )
            Spacer(Modifier.height(24.dp))
            ProviderCell(
                provider = ProviderItem(id = "moonpay", title = "MoonPay", imageUrl = "", isBest = true),
                subtitle = "Min. amount: 25 USD",
                isSubtitleWarning = true,
                onClick = {},
            )
        }
    }
}
