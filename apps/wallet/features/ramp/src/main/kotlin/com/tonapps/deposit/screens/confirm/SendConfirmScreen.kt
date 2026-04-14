package com.tonapps.deposit.screens.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.core.extensions.iconExternalUrl
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.deposit.utils.formatDuration
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Links
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import kotlinx.coroutines.launch
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLoadingPreviewImage
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonDescriptionCell
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.TextCell
import ui.components.moon.cell.rememberSliderState
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonCutRow
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonAlertDialog
import ui.preview.ThemedPreview
import ui.text.withLink
import ui.theme.LocalAppColorScheme
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer
import ui.utils.toRichSpanStyle

@Composable
fun SendConfirmScreen(
    feature: ConfirmFeature,
    onClose: () -> Unit,
    onSendSuccess: () -> Unit,
    onBack: () -> Unit,
    onBuyTon: () -> Unit = {},
    onGetTrx: () -> Unit = {},
    onRechargeBattery: () -> Unit = {},
) {
    val state by feature.state.global.observeSafeState()
    val lifecycle = LocalLifecycleOwner.current

    var insufficientFundsEvent by remember {
        mutableStateOf<ConfirmEvent.ShowInsufficientBalance?>(null)
    }

    LaunchedEffect(Unit) {
        feature.events.flowWithLifecycle(lifecycle.lifecycle).collect { event ->
            when (event) {
                is ConfirmEvent.ShowInsufficientBalance -> insufficientFundsEvent = event
            }
        }
    }

    insufficientFundsEvent?.let { event ->
        InsufficientFundsDialog(
            event = event,
            onBuyTon = onBuyTon,
            onGetTrx = onGetTrx,
            onRechargeBattery = onRechargeBattery,
            onClose = {
                insufficientFundsEvent = null
                onBack()
            },
        )
    }

    when (val s = state) {
        is ConfirmState.Loading -> {
            SendConfirmShimmer(onClose = onClose, onBack = onBack)
        }
        is ConfirmState.Ready -> {
            SendConfirmContent(
                state = s,
                feature = feature,
                onClose = onClose,
                onBack = onBack,
                onSendSuccess = onSendSuccess,
            )
        }
        is ConfirmState.Error -> {
            // TODO
        }
    }
}

@Composable
private fun SendConfirmContent(
    state: ConfirmState.Ready,
    feature: ConfirmFeature,
    onClose: () -> Unit,
    onSendSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val clipboardManager = rememberClipboardManager()
    var showFeePopup by remember { mutableStateOf(false) }
    var showSendAllConfirmation by remember { mutableStateOf(false) }
    val sliderState = rememberSliderState()
    val scope = rememberCoroutineScope()

    MoonScaffold(
        Modifier
            .navigationBarsPadding(),
        title = if (state.isExchangeMode) stringResource(Localization.withdraw) else stringResource(Localization.send),
        onClose = onClose,
        onBack = onBack,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .nestedScroll(rememberNestedScrollInteropConnection()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val context = LocalContext.current

            when {
                state.exchangeData != null && state.token != null -> {
                    HeaderCell(
                        from = state.token.asCurrency,
                        to = state.exchangeData.currency,
                    )
                }
                state.token != null -> {
                    val chainIcon = remember(state.token) {
                        if (state.token.tokenType != null) state.token.asCurrency.chain.iconExternalUrl(context) else null
                    }

                    MoonCutBadgedBox(
                        badge = {
                            chainIcon?.let {
                                MoonItemImage(
                                    image = chainIcon,
                                    size = 28.dp
                                )
                            }
                        },
                        direction = BadgeDirection.EndBottom,
                    ) {
                        MoonItemImage(
                            image = state.token.imageUri.toString(),
                            size = 96.dp,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(Localization.jetton_transfer, state.token.symbol),
                        color = UIKit.colorScheme.text.secondary,
                        style = UIKit.typography.body1,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            MoonBundleCell {
                Column {
                    MoonPropertyBigCell(
                        title = stringResource(Localization.wallet),
                        value = state.walletName,
                        valueDescription = null,
                        onClick = { clipboardManager.copy(state.walletName) }
                    )

                    MoonItemDivider()

                    if (!state.isExchangeMode && state.recipientDisplay != null) {
                        TextCell(
                            title = { MoonItemTitle(stringResource(Localization.recipient), color = UIKit.colorScheme.text.secondary) },
                            subtitle = {
                                MoonItemTitle(
                                    text = state.recipientDisplay,
                                    maxLines = 2,
                                )
                            },
                            minHeight = 82.dp,
                            onClick = { clipboardManager.copy(state.recipientDisplay) }
                        )

                        MoonItemDivider()

                        TextCell(
                            title = { MoonItemTitle(stringResource(Localization.recipient_address), color = UIKit.colorScheme.text.secondary) },
                            subtitle = {
                                MoonItemTitle(
                                    text = state.recipientAddress,
                                    maxLines = 2,
                                )
                            },
                            minHeight = 82.dp,
                            onClick = { clipboardManager.copy(state.recipientAddress) }
                        )
                    } else {
                        val address = state.recipientDisplay ?: state.recipientAddress
                        TextCell(
                            title = { MoonItemTitle(stringResource(Localization.recipient), color = UIKit.colorScheme.text.secondary) },
                            subtitle = {
                                MoonItemTitle(
                                    text = address,
                                    maxLines = 2,
                                )
                            },
                            minHeight = 82.dp,
                            onClick = { clipboardManager.copy(address) }
                        )
                    }

                    state.displayCurrency?.let { currency ->
                        MoonItemDivider()

                        MoonPropertyBigCell(
                            title = stringResource(Localization.network),
                            value = currency.chain.name,
                            valueDescription = currency.tokenType?.fmt,
                        )
                    }

                    state.withdrawalFeeFormatted?.let { fee ->
                        MoonItemDivider()

                        MoonPropertyBigCell(
                            title = stringResource(Localization.ramp_withdrawal_fee),
                            value = fee,
                            valueDescription = state.withdrawalFeeFiatFormatted,
                            onClick = { clipboardManager.copy(state.withdrawalFeeFiatFormatted.toString()) }
                        )
                    }

                    MoonItemDivider()

                    Box {
                        MoonPropertyBigCell(
                            title = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    MoonItemTitle(
                                        modifier = Modifier,
                                        text = stringResource(Localization.network_fee),
                                        color = UIKit.colorScheme.text.secondary,
                                    )

                                    if (state.hasMultipleFeeOptions) {
                                        MoonItemSubtitle(
                                            text = stringResource(Localization.edit_full),
                                            color = UIKit.colorScheme.text.accent,
                                        )
                                    }
                                }
                            },
                            content = {
                                MoonItemTitle(text = state.feeFormatted)

                                DropdownMenu(
                                    containerColor = UIKit.colorScheme.background.contentTint,
                                    shape = UIKit.shapes.large,
                                    expanded = showFeePopup,
                                    onDismissRequest = { showFeePopup = false },
                                ) {
                                    Column {
                                        state.feeOptions.forEach { fee ->
                                            FeeItemCell(
                                                image = { FeeIcon(fee) },
                                                title = feeTitle(fee),
                                                subtitle = feeSubtitle(fee),
                                                isChecked = fee == state.selectedFee,
                                                onClick = {
                                                    showFeePopup = false
                                                    feature.sendAction(ConfirmAction.SelectFee(fee))
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                            contentDescription = { MoonItemSubtitle(text = state.feeFiatFormatted) },
                            onClick = if (state.hasMultipleFeeOptions) {
                                { showFeePopup = true }
                            } else {
                                null
                            },
                        )
                    }

                    state.totalFormatted?.let { total ->
                        MoonItemDivider()

                        MoonPropertyBigCell(
                            title = stringResource(Localization.ramp_total_amount),
                            value = total,
                            valueDescription = state.totalFiatFormatted,
                        )
                    }

                    if (!state.isExchangeMode) {
                        MoonItemDivider()

                        MoonPropertyBigCell(
                            title = stringResource(Localization.amount),
                            value = state.amountFormatted,
                            valueDescription = state.amountFiatFormatted,
                            onClick = { clipboardManager.copy(state.amountFormatted.toString()) },
                        )
                    }

                    state.estimatedDurationSeconds?.let { duration ->
                        MoonItemDivider()

                        MoonPropertyBigCell(
                            title = stringResource(Localization.ramp_withdrawal_time),
                            value = formatDuration(duration),
                            valueDescription = null,
                        )
                    }

                    if (state.comment != null && !state.isExchangeMode) {
                        MoonItemDivider()

                        TextCell(
                            modifier = Modifier.padding(vertical = 8.dp),
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MoonItemTitle(stringResource(Localization.comment), color = UIKit.colorScheme.text.secondary)
                                    if (state.encryptedComment) {
                                        MoonItemIcon(painterResource(UIKitIcon.ic_lock_16), color = UIKit.colorScheme.accent.green)
                                    }
                                }
                            },
                            subtitle = {
                                MoonItemTitle(
                                    text = state.comment,
                                    maxLines = 10,
                                )
                            },
                            minHeight = 82.dp,
                            onClick = { clipboardManager.copy(state.comment) }
                        )
                    }
                }
            }

            if (state.displayCurrency != null) {
                val style = UIKit.typography.body2
                val color = UIKit.colorScheme.text.accent
                val termsText = stringResource(Localization.terms_of_service)
                val disclaimerTemplate = stringResource(Localization.deposit_changelly_disclaimer, termsText)
                MoonDescriptionCell(
                    text = remember(disclaimerTemplate) {
                        buildAnnotatedString {
                            val termsStart = disclaimerTemplate.indexOf(termsText)
                            if (termsStart >= 0) {
                                append(disclaimerTemplate.substring(0, termsStart))
                                withLink(
                                    text = termsText,
                                    link = Links.ChangellyTerms,
                                    style = style,
                                    color = color,
                                )
                                append(disclaimerTemplate.substring(termsStart + termsText.length))
                            } else {
                                append(disclaimerTemplate)
                            }
                        }
                    }
                )
            }
        }

        val errorText = when (state.error) {
            is ConfirmationError.Unknown -> stringResource(Localization.unknown_error)
            is ConfirmationError.InsufficientBalance -> stringResource(Localization.insufficient_balance)
            is ConfirmationError.Message -> state.error.text
            null -> null
        }

        val confirmationState = when (state.signingState) {
            SigningState.Idle -> MoonSlideConfirmationState.Slider
            SigningState.Loading -> MoonSlideConfirmationState.Loader
            SigningState.Success -> MoonSlideConfirmationState.Done
            SigningState.Failed -> MoonSlideConfirmationState.Slider
        }

        val context = LocalContext.current

        MoonSlideConfirmation(
            state = confirmationState,
            sliderState = sliderState,
            error = errorText,
            title = when (confirmationState) {
                MoonSlideConfirmationState.Slider,
                MoonSlideConfirmationState.Loader -> stringResource(Localization.confirm_action)

                MoonSlideConfirmationState.Done -> stringResource(Localization.done)
            },
            enabled = state.signingState == SigningState.Idle,
            onConfirm = {
                if (state.isMax) {
                    showSendAllConfirmation = true
                } else {
                    feature.sendAction(ConfirmAction.Sign(context))
                }
            },
            onRetry = { feature.sendAction(ConfirmAction.Retry(context)) },
            onDone = { onSendSuccess() },
        )

        if (showSendAllConfirmation) {
            MoonAlertDialog(
                message = stringResource(Localization.send_all_balance),
                positiveButtonText = stringResource(Localization.continue_action),
                negativeButtonText = stringResource(Localization.cancel),
                onPositiveClick = {
                    showSendAllConfirmation = false
                    feature.sendAction(ConfirmAction.Sign(context))
                },
                onDismiss = {
                    showSendAllConfirmation = false
                    scope.launch { sliderState.reset() }
                },
            )
        }
    }
}
@Composable
private fun HeaderCell(
    from: WalletCurrency,
    to: WalletCurrency,
) {
    val headerStyle = UIKit.typography.h2
    val headerColor = UIKit.colorScheme.text.primary
    val headerSecondaryColor = UIKit.colorScheme.text.secondary

    val fromChainName = from.tokenType?.fmt ?: from.chain.symbol
    val toChainName = to.tokenType?.fmt ?: to.chain.symbol
    val sendLabel = stringResource(Localization.send_currency, from.code)
    val receiveLabel = stringResource(Localization.receive_currency, to.code)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoonCutRow {
            MoonItemImage(size = 72.dp, image = from.iconUri?.toString())
            MoonItemImage(size = 72.dp, image = to.iconUri?.toString())
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(Localization.confirm_action),
            color = UIKit.colorScheme.text.secondary,
            style = UIKit.typography.body1,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = remember(LocalAppColorScheme.current, from, sendLabel) {
                buildAnnotatedString {
                    withStyle(headerStyle.toRichSpanStyle(color = headerColor)) {
                        append("$sendLabel ")
                    }

                    withStyle(headerStyle.toRichSpanStyle(color = headerSecondaryColor)) {
                        append(fromChainName)
                    }
                }
            },
            textAlign = TextAlign.Center,
        )

        Text(
            text = remember(LocalAppColorScheme.current, to, receiveLabel) {
                buildAnnotatedString {
                    withStyle(headerStyle.toRichSpanStyle(color = headerColor)) {
                        append("$receiveLabel ")
                    }

                    withStyle(headerStyle.toRichSpanStyle(color = headerSecondaryColor)) {
                        append(toChainName)
                    }
                }
            },
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FeeIcon(fee: SendFee) {
    when (fee) {
        is SendFee.Battery -> MoonItemIcon(
            painterResource(UIKitIcon.ic_flash_24),
            color = UIKit.colorScheme.accent.green,
        )
        is SendFee.Ton, is SendFee.TronTon -> MoonItemImage(
            painterResource(UIKitIcon.ic_ton),
            size = 24.dp,
        )
        is SendFee.TronTrx -> MoonItemImage(
            painterResource(UIKitIcon.ic_tron),
            size = 24.dp,
        )
        is SendFee.Gasless -> MoonItemIcon(
            painterResource(UIKitIcon.ic_flash_24),
            color = UIKit.colorScheme.accent.blue,
        )
    }
}

@Composable
private fun feeTitle(fee: SendFee): String {
    return when (fee) {
        is SendFee.Battery -> stringResource(Localization.battery_refill_title)
        is SendFee.Ton -> fee.amount.token.symbol
        is SendFee.Gasless -> fee.amount.token.symbol
        is SendFee.TronTrx -> fee.amount.token.symbol
        is SendFee.TronTon -> "TON"
    }
}

@Composable
private fun feeSubtitle(fee: SendFee): String {
    return when (fee) {
        is SendFee.TokenFee -> {
            when (fee) {
                is SendFee.TronTrx if !fee.enoughBalance -> stringResource(Localization.no_enough_funds)
                is SendFee.TronTon if !fee.enoughBalance -> stringResource(Localization.no_enough_funds)
                else -> {
                    val formatted = CurrencyFormatter.format(fee.amount.token.symbol, fee.amount.value)
                    val fiatFormatted = CurrencyFormatter.formatFiat(fee.fiatCurrency.code, fee.fiatAmount)
                    "≈ $fiatFormatted ($formatted)"
                }
            }
        }
        is SendFee.Battery -> {
            if (!fee.enoughCharges) {
                stringResource(Localization.no_enough_funds)
            } else {
                val formattedCharges = pluralStringResource(
                    Plurals.battery_charges,
                    fee.charges,
                    CurrencyFormatter.format(value = fee.charges.toBigDecimal())
                )

                remember(fee) {
                    val formattedFiat = CurrencyFormatter.formatFiat(fee.fiatCurrency.code, fee.fiatAmount)
                    "≈ $formattedFiat ($formattedCharges)"
                }
            }
        }
    }
}

// TODO remove this
@Composable
private fun SendConfirmShimmer(
    onClose: () -> Unit,
    onBack: () -> Unit,
) {
    val shimmer by rememberShimmerPhase()

    MoonScaffold(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        title = stringResource(Localization.send),
        onClose = onClose,
        onBack = onBack,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .nestedScroll(rememberNestedScrollInteropConnection()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MoonLoadingPreviewImage(size = 96.dp)

            Spacer(Modifier.height(16.dp))

            Spacer(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(20.dp)
                    .shimmer(shimmer)
            )

            Spacer(Modifier.height(4.dp))

            Spacer(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(28.dp)
                    .shimmer(shimmer)
            )

            Spacer(Modifier.height(16.dp))

            MoonBundleCell {
                Column {
                    MoonPropertyBigCell(
                        title = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.25f)
                                    .height(20.dp)
                                    .shimmer(shimmer)
                            )
                        },
                        content = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.3f)
                                    .height(20.dp)
                                    .shimmer(shimmer)
                            )
                        },
                    )

                    MoonItemDivider()

                    MoonPropertyBigCell(
                        title = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.25f)
                                    .height(20.dp)
                                    .shimmer(shimmer)
                            )
                        },
                        content = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(20.dp)
                                    .shimmer(shimmer)
                            )
                        },
                    )

                    MoonItemDivider()

                    MoonPropertyBigCell(
                        title = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.25f)
                                    .height(20.dp)
                                    .shimmer(shimmer)
                            )
                        },
                        content = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.3f)
                                    .height(20.dp)
                                    .shimmer(shimmer)
                            )
                        },
                        contentDescription = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.25f)
                                    .height(16.dp)
                                    .shimmer(shimmer)
                            )
                        },
                    )

                    MoonItemDivider()

                    MoonPropertyBigCell(
                        title = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.25f)
                                    .height(20.dp)
                                    .shimmer(shimmer)
                            )
                        },
                        content = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.3f)
                                    .height(20.dp)
                                    .shimmer(shimmer)
                            )
                        },
                        contentDescription = {
                            Spacer(
                                Modifier
                                    .fillMaxWidth(0.25f)
                                    .height(16.dp)
                                    .shimmer(shimmer)
                            )
                        },
                    )
                }
            }
        }

        MoonSlideConfirmation(
            state = MoonSlideConfirmationState.Slider,
            modifier = Modifier.shimmer(shimmer),
            title = stringResource(Localization.confirm_action),
            enabled = false,
            onConfirm = {},
            onDone = {},
        )
    }
}

// TODO to design system
@Composable
fun MoonPropertyBigCell(
    title: CharSequence,
    value: CharSequence,
    valueDescription: CharSequence?,
    onClick: (() -> Unit)? = null,
) {
    MoonPropertyBigCell(
        title = {
            MoonItemTitle(
                modifier = Modifier,
                text = title,
                color = UIKit.colorScheme.text.secondary,
            )
        },
        content = {
            MoonItemTitle(text = value)
        },
        contentDescription = valueDescription?.let {
            { MoonItemSubtitle(text = it) }
        },
        onClick = onClick,
    )
}

// TODO to design system
@Composable
fun MoonPropertyBigCell(
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    contentDescription: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .padding(16.dp)
            .modifyIf { onClick?.let { clickable(onClick = it) } },
    ) {
        title()

        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            content()

            contentDescription?.let {
                contentDescription.invoke()
            }
        }
    }
}


@Preview
@Composable
private fun SendConfirmScreenPreview() {
    ThemedPreview {
        MoonSurface {
            Column(Modifier.fillMaxSize()) {
               DropdownMenu(
                   modifier = Modifier.background(color = UIKit.colorScheme.background.content),
                   containerColor = UIKit.colorScheme.background.contentTint,
                   shape = UIKit.shapes.large,
                   expanded = true,
                   onDismissRequest = { },
               ) {
                   Column {
                       FeeItemCell(
                           image = { MoonItemIcon(painterResource(UIKitIcon.ic_flash_24), color = UIKit.colorScheme.accent.green) },
                           title = "Tonkeeper Battery",
                           subtitle = "≈ 0.13 - 0.27 TON $0.07",
                           isChecked = false,
                           onClick = { },
                       )

                       FeeItemCell(
                           image = { MoonItemImage(painterResource(UIKitIcon.ic_ton), size = 24.dp) },
                           title = "TON",
                           subtitle = "≈ 0.13 - 0.27 TON $0.07",
                           isChecked = true,
                           onClick = { },
                       )
                   }
               }
            }
        }
    }
}

@Preview
@Composable
private fun ExpandendScreen() {
    ThemedPreview {
        Column(
            Modifier.fillMaxSize()
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {

            }

            MoonSlideConfirmation(
                state = MoonSlideConfirmationState.Slider,
                title = stringResource(Localization.confirm_action),
                enabled = false,
                onConfirm = {},
                onDone = {},
            )
        }
    }
}

