package com.tonapps.deposit.screens.ramp.amount

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.tonapps.deposit.screens.provider.ProviderConfirmDialog
import com.tonapps.deposit.screens.provider.ProviderItem
import com.tonapps.deposit.screens.provider.ProviderWithQuote
import com.tonapps.deposit.screens.provider.SelectProviderDialog
import com.tonapps.deposit.utils.AmountError
import com.tonapps.deposit.utils.ProviderAmountState
import com.tonapps.deposit.utils.rememberProviderAmountState
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer
import ui.theme.outlineStoke
import ui.utils.toRichSpanStyle
import ui.utils.uppercased
import java.math.RoundingMode

@Composable
fun DepositAmountScreen(
    feature: DepositAmountFeature,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onContinue: (String) -> Unit,
) {
    DepositAmountContent(
        feature = feature,
        onBack = onBack,
        onClose = onClose,
        onProviderSelected = { feature.sendAction(DepositAmountAction.SelectProvider(it)) },
        onAmountInput = feature::onAmountInput,
        onToggleInputCurrency = {}, // TODO
        onContinue = onContinue,
    )
}

@Composable
private fun DepositAmountContent(
    feature: DepositAmountFeature,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onAmountInput: (String) -> Unit,
    onToggleInputCurrency: () -> Unit,
    onContinue: (String) -> Unit,
) {
    val state by feature.state.global.observeSafeState()
    val lifecycle = LocalLifecycleOwner.current
    var showConfirmationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        feature.events.flowWithLifecycle(lifecycle.lifecycle)
            .collect {
                when (it) {
                    is DepositAmountEvent.ShowConfirmation -> showConfirmationDialog = true
                    is DepositAmountEvent.Continue -> {
                        showConfirmationDialog = false
                        onContinue(it.providerUrl)
                    }
                }
            }
    }

    MoonScaffold(
        Modifier.imePadding(),
        title = stringResource(Localization.deposit_insert_amount),
        onClose = onClose,
        onBack = onBack,
    ) {
        when (val state = state) {
            is DepositAmountState.Loading -> MoonLoaderCell()
            is DepositAmountState.Data -> {
                DataContent(
                    state = state,
                    onProviderSelected = onProviderSelected,
                    onAmountInput = onAmountInput,
                    onToggleInputCurrency = onToggleInputCurrency,
                    onContinue = { feature.sendAction(DepositAmountAction.CheckProvider(it)) },
                )

                val selected = state.selectedProvider
                if (showConfirmationDialog && selected != null) {
                    val style = UIKit.typography.body1
                    val color = UIKit.colorScheme.text.accent
                    val buttons = selected.info.buttons
                    val description = remember(color, buttons) {
                        if (buttons.isEmpty()) return@remember null
                        buildAnnotatedString {
                            val linkStyle = style.toRichSpanStyle(color = color)
                            buttons.forEachIndexed { index, button ->
                                if (index > 0) append(" · ")
                                withLink(
                                    LinkAnnotation.Url(
                                        url = button.url,
                                        styles = TextLinkStyles(linkStyle),
                                    )
                                ) { append(button.title) }
                            }
                        }
                    }

                    ProviderConfirmDialog(
                        title = selected.info.title,
                        icon = selected.info.imageUrl,
                        description = description,
                        onClose = { showConfirmationDialog = false },
                        onConfirm = {
                            feature.sendAction(
                                DepositAmountAction.AllowProvider(
                                    selected,
                                    it
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DataContent(
    state: DepositAmountState.Data,
    onProviderSelected: (String) -> Unit,
    onAmountInput: (String) -> Unit,
    onToggleInputCurrency: () -> Unit,
    onContinue: (ProviderWithQuote) -> Unit,
) {
    val amountState = rememberProviderAmountState(
        provider = state.selectedProvider?.info ?: state.selectedErrorProvider,
    )

    LaunchedEffect(state.initialAmount) {
        val initial = state.initialAmount
        if (initial != null && amountState.text.isEmpty()) {
            amountState.onTextChange(initial)
        }
    }

    val selected = state.selectedProvider ?: state.providers.firstOrNull()
    var showProviderDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            AmountInputCell(
                amountState = amountState,
                currencyCode = state.inputCurrencyCode,
                receiveAmount = selected?.quote?.receiveAmount ?: state.defaultAmount,
                onAmountInput = onAmountInput,
                onToggleInputCurrency = {
                    val newAmount = state.selectedProvider?.quote?.receiveCoins
                        ?.let { Coins.string(it) }
                    onToggleInputCurrency()
                    if (newAmount != null) {
                        amountState.onTextChange(newAmount)
                        onAmountInput(newAmount)
                    }
                },
                isLoading = state.isCalculating,
                onDone = {
                    if (amountState.isValid) {
                        state.selectedProvider?.let { onContinue(it) }
                    }
                }
            )

            state.balance?.let { balance ->
                val insufficientBalance = amountState.coins.isPositive && amountState.coins > balance.uiBalance
                val availableFormatted = remember(balance) {
                    CurrencyFormatter.format(
                        currency = balance.token.symbol,
                        value = balance.uiBalance,
                        roundingMode = RoundingMode.DOWN,
                        replaceSymbol = false,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MoonBadgeButton(
                        content = {
                            MoonSmallItemTitle(stringResource(Localization.max).uppercased())
                        },
                        onClick = {
                            val maxAmount = Coins.string(balance.uiBalance)
                            amountState.onTextChange(maxAmount)
                            onAmountInput(maxAmount)
                        },
                    )

                    if (insufficientBalance) {
                        MoonItemSubtitle(
                            text = stringResource(Localization.insufficient_balance),
                            color = UIKit.colorScheme.accent.red,
                        )
                    } else {
                        MoonItemSubtitle(
                            text = stringResource(Localization.available, availableFormatted),
                            color = UIKit.colorScheme.text.secondary,
                        )
                    }
                }
            }

            if (state.isCalculating) {
                val shimmer by rememberShimmerPhase()
                ProviderCell(
                    modifier = Modifier.shimmer(shimmer),
                    provider = ProviderItem.EMPTY,
                    rateFormatted = "",
                    onClick = null,
                )
            } else if (selected?.quote != null) {
                val keyboardController = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                ProviderCell(
                    provider = selected.info,
                    rateFormatted = selected.rate?.rateFormatted,
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus(true)
                        showProviderDialog = true
                    },
                )
            } else if (state.providers.isEmpty()) {
                EmptyProviderCell()
            }
        }

        val insufficientBalance = state.balance?.let { amountState.coins.isPositive && amountState.coins > it.uiBalance } ?: false
        MoonButtonCell(
            text = stringResource(Localization.continue_action),
            enabled = state.canContinue
                    && amountState.isValid
                    && !insufficientBalance
                    && amountState.coins.isSame(state.selectedProvider?.quote?.amount),
        ) {
            state.selectedProvider?.let { onContinue(it) }
        }
    }

    if (showProviderDialog) {
        SelectProviderDialog(
            providers = state.providers,
            selectedProviderId = state.selectedProviderId,
            currencyCode = state.inputCurrencyCode,
            onConfirm = { providerId, minAmount ->
                if (minAmount != null) {
                    val amountStr = Coins.string(minAmount)
                    amountState.onTextChange(amountStr)
                    onAmountInput(amountStr)
                }
                onProviderSelected(providerId)
            },
            onClose = { showProviderDialog = false },
        )
    }
}

@Composable
private fun AmountInputCell(
    amountState: ProviderAmountState,
    currencyCode: String,
    receiveAmount: String,
    isLoading: Boolean,
    onAmountInput: (String) -> Unit,
    onToggleInputCurrency: () -> Unit,
    onDone: () -> Unit,
) {
    MoonBundleCell {
        Column(
            Modifier
                .padding(remember {
                    PaddingValues(
                        top = 39.dp,
                        bottom = 6.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                })
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
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
                        .horizontalScroll(scrollState)
                        .width(IntrinsicSize.Min)
                        .defaultMinSize(minWidth = 24.dp),
                    value = amountState.value,
                    onValueChange = { newValue ->
                        amountState.onValueChange(newValue)
                        onAmountInput(amountState.value.text)
                    },
                    textStyle = style,
                    keyboardActions = remember {
                        KeyboardActions(onDone = { onDone() })
                    },
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

                    onDispose {
                        focusRequester.freeFocus()
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
//                    .clickable(onClick = onToggleInputCurrency)
                    .border(
                        border = outlineStoke(),
                        shape = CircleShape
                    )
                    .run {
                        if (isLoading) {
                            val shimmer by rememberShimmerPhase()
                            shimmer(shimmer)
                        } else {
                            this
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = receiveAmount,
                    color = UIKit.colorScheme.text.secondary,
                    style = UIKit.typography.body1,
                )

//                MoonItemIcon(
//                    painter = painterResource(UIKitIcon.ic_swap_vertical_16),
//                    color = UIKit.colorScheme.icon.secondary,
//                )
            }

            val error = if (isLoading) null else amountState.error
            MoonItemSubtitle(
                modifier = Modifier
                    .alpha(if (error != null) 1f else 0f)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                text = when (error) {
                    is AmountError.AboveMax -> stringResource(Localization.max_amount, error.maxAmount.value)
                    is AmountError.BelowMin -> stringResource(Localization.min_amount, error.minAmount.value)
                    null -> ""
                },
                color = UIKit.colorScheme.accent.red,
            )
        }
    }
}

@Composable
private fun EmptyProviderCell() {
    MoonBundleCell(
        contentPadding = remember { PaddingValues(16.dp) }
    ) {
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
    rateFormatted: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    MoonBundleCell(
        contentPadding = remember { PaddingValues(16.dp) }
    ) {
        TextCell(
            modifier = modifier,
            title = provider.title,
            subtitle = rateFormatted,
            tags = {
                if (provider.isBest) {
                    MoonLabel(stringResource(Localization.best), colors = MoonLabelDefault.blue())
                }
            },
            onClick = onClick,
            content = {
                MoonItemIcon(painterResource(UIKitIcon.ic_switch_16))
            },
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
