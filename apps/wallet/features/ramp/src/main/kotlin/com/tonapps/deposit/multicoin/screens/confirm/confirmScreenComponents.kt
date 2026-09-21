package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.core.LocalIsTopFragment
import com.tonapps.deposit.multicoin.screens.confirm.engine.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFeeLogic
import com.tonapps.deposit.screens.confirm.SendConfirmShimmerBody
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.ButtonColorsOrange
import ui.components.moon.ButtonColorsRed
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.cell.rememberSliderState
import ui.components.moon.dialog.MoonAlertDialog
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.components.moon.container.MoonScaffold
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer

/**
 * Shared scaffold for the multichain confirm screens. Handles the loading skeleton, completion event,
 * the scrollable body ([content]), an optional [footer] above the action slot, and the slide-to-confirm
 * action together with the send-all and price-impact warning dialogs.
 *
 * The action slot is rendered once for every stage: it carries the slider while there is something to
 * confirm and the error with its retry otherwise, so a failure never has a second place to show up in.
 *
 * [TxConfirmScreen] and [SwapConfirmScreen] supply the body that differs between transfers and swaps.
 */
@Composable
internal fun ConfirmScreenScaffold(
    feature: BaseConfirmFeature,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onConfirm: () -> Unit = {},
    footer: @Composable ColumnScope.(tx: PendingTransaction) -> Unit = {},
    content: @Composable ColumnScope.(tx: PendingTransaction) -> Unit,
) {
    val pendingTx by feature.pendingTx.collectAsState(initial = null)
    val pendingLoader by feature.pendingLoader.collectAsState()
    val confirmationError by feature.confirmationError.collectAsState()
    val sendingState by feature.sendingState.collectAsState()

    // Coming back from the battery refill or receive flow must re-estimate. Navigation only adds
    // fragments on top, so this sheet never pauses — the top-fragment flag is the only signal.
    val isTopFragment = LocalIsTopFragment.current
    var wasCovered by remember { mutableStateOf(false) }
    LaunchedEffect(isTopFragment) {
        if (!isTopFragment) {
            wasCovered = true
        } else if (wasCovered) {
            wasCovered = false
            feature.refreshFees()
        }
    }

    LaunchedEffect(Unit) {
        feature.events.collect {
            when (it) {
                ConfirmEvent.Done -> onSendSuccess()
            }
        }
    }

    // A transaction under re-preparation is not something to confirm yet, and its error belongs to the
    // attempt that is already gone: both wait until the new one lands.
    val tx = pendingTx.takeIf { !pendingLoader }
    val error = confirmationError.takeIf { !pendingLoader }

    ConfirmContent(
        feature = feature,
        tx = tx,
        sendingState = sendingState,
        error = error,
        onClose = onClose,
        onBack = onBack,
        onSendSuccess = onSendSuccess,
        onConfirm = onConfirm,
        onTopUp = onTopUp,
        footer = footer,
        content = content,
        onOpenBattery = onOpenBattery,
    )
}

@Composable
private fun ConfirmContent(
    feature: BaseConfirmFeature,
    tx: PendingTransaction?,
    sendingState: SendingState,
    error: ConfirmationError?,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onTopUp: (assetId: String) -> Unit,
    onConfirm: () -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    footer: @Composable ColumnScope.(tx: PendingTransaction) -> Unit,
    content: @Composable ColumnScope.(tx: PendingTransaction) -> Unit,
) {
    val sliderState = rememberSliderState()
    val scope = rememberCoroutineScope()
    var showSendAllConfirmation by remember { mutableStateOf(false) }
    var showPriceImpactWarning by remember { mutableStateOf(false) }

    val phase by rememberShimmerPhase()
    // Frozen while an error is up: a sweeping skeleton reads as "still loading" right above a slot
    // that says the opposite.
    val shimmer = when (error) {
        null -> phase
        else -> 0f
    }

    val priceImpact = tx?.priceImpact()
    val pickedFeeId by feature.pickedFeeId.collectAsState()
    val selectedFee = remember(tx, pickedFeeId) {
        tx?.fee?.resolve(pickedFeeId)
    }
    val feeInsufficient = selectedFee != null && !TxFeeLogic.isSufficient(selectedFee)
    val outcomeUnknown = error == ConfirmationError.OutcomeUnknown

    val context = LocalContext.current
    val sign: () -> Unit = {
        if (tx != null) {
            onConfirm()
            feature.sendTransaction(context, tx)
        }
    }
    val retry: () -> Unit = { feature.retry(context) }

    // Single thumb-reset path: covers a cancelled passcode dialog as well as a cleared error, and
    // keeps concurrent animateTo calls off the slider Animatable.
    LaunchedEffect(sendingState, error) {
        if (sendingState == SendingState.None && error == null && sliderState.isConfirmed) {
            sliderState.reset()
        }
    }

    MoonScaffold(
        modifier = Modifier.padding(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        title = "",
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
            if (tx == null) {
                SendConfirmShimmerBody(shimmer)
            } else {
                content(tx)
            }
        }

        if (tx != null) {
            footer(tx)
        }

        val isMax = tx?.isMax()
        val confirmationState = when (sendingState) {
            SendingState.None -> MoonSlideConfirmationState.Slider
            SendingState.Loading -> MoonSlideConfirmationState.Loader
            SendingState.Done -> MoonSlideConfirmationState.Done
        }

        val impactWarning = priceImpact?.takeIf { it.severity != PriceImpactSeverity.Normal }
        val sliderThumbColor = when (impactWarning?.severity) {
            PriceImpactSeverity.Danger -> UIKit.colorScheme.accent.red
            PriceImpactSeverity.Warning -> UIKit.colorScheme.accent.orange
            else -> UIKit.colorScheme.buttonPrimary.primaryBackground
        }

        MoonSlideConfirmation(
            state = confirmationState,
            modifier = when {
                tx != null || error != null -> Modifier
                else -> Modifier.shimmer(shimmer)
            },
            sliderState = sliderState,
            error = error?.errorText,
            thumbColor = sliderThumbColor,
            title = when (confirmationState) {
                MoonSlideConfirmationState.Slider,
                MoonSlideConfirmationState.Loader -> stringResource(Localization.confirm)

                MoonSlideConfirmationState.Done -> stringResource(Localization.done)
            },
            subtitle = stringResource(Localization.swipe_right),
            buttonTitle = stringResource(Localization.try_again),
            enabled = tx != null && sendingState == SendingState.None && !feeInsufficient && !outcomeUnknown,
            onConfirm = {
                when {
                    isMax == true -> showSendAllConfirmation = true
                    impactWarning != null -> showPriceImpactWarning = true
                    else -> sign()
                }
            },
            onClick = retry.takeIf { !outcomeUnknown },
            onDone = onSendSuccess,
        )

        if (showPriceImpactWarning && impactWarning != null) {
            PriceImpactWarningDialog(
                severity = impactWarning.severity,
                impact = impactWarning.text,
                onConfirm = {
                    showPriceImpactWarning = false
                    sign()
                },
                onClose = {
                    showPriceImpactWarning = false
                    scope.launch { sliderState.reset() }
                },
            )
        }

        if (error == ConfirmationError.InsufficientBalance && tx != null) {
            // Leaving the dialog also leaves the confirm screen, so the user re-enters the flow
            // fresh after topping up instead of returning to a stale emulation.
            val leaveScreen = { onBack?.invoke() ?: onClose() }
            tx.InsufficientFundsDialog(
                onTopUp = onTopUp,
                onOpenBattery = onOpenBattery,
                onClose = leaveScreen,
            )
        }

        if (showSendAllConfirmation) {
            MoonAlertDialog(
                message = stringResource(Localization.send_all_balance),
                positiveButtonText = stringResource(Localization.continue_action),
                negativeButtonText = stringResource(Localization.cancel),
                onPositiveClick = {
                    showSendAllConfirmation = false
                    sign()
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
internal fun FeeCell(fee: Pair<String, String?>, onClick: (() -> Unit)? = null) {
    MoonItemDivider()
    MoonPropertyBigCell(
        title = {
            Column {
                MoonLargeItemSubtitle(
                    text = stringResource(Localization.network_fee),
                    color = UIKit.colorScheme.text.secondary,
                )
                if (onClick != null) {
                    MoonItemSubtitle(
                        text = stringResource(Localization.edit_full),
                        color = UIKit.colorScheme.text.accent,
                    )
                }
            }
        },
        content = {
            MoonPropertyValue(
                title = fee.first,
                subtitle = fee.second,
            )
        },
        onClick = onClick,
    )
}

/**
 * Fee row when there is a method to pick: value, then the paying asset as the tappable part.
 */
@Composable
private fun FeeSelectorCell(
    value: String,
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val symbolColor = if (enabled) {
        UIKit.colorScheme.text.accent
    } else {
        UIKit.colorScheme.text.secondary
    }

    MoonItemDivider()
    MoonPropertyBigCell(
        title = {
            MoonLargeItemSubtitle(
                text = stringResource(Localization.network_fee),
                color = UIKit.colorScheme.text.secondary,
            )
        },
        content = {
            MoonPropertyValue(
                title = value,
                content = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MoonItemTitle(text = "·")
                        MoonItemTitle(
                            text = symbol,
                            color = symbolColor,
                        )
                        if (enabled) {
                            MoonItemIcon(
                                painter = painterResource(UIKitIcon.ic_switch_16),
                                color = UIKit.colorScheme.text.accent,
                            )
                        }
                    }
                },
            )
        },
        onClick = onClick.takeIf { enabled },
    )
}

/**
 * Fee row for the multichain fee-option flows: renders the selected method and opens the picker when
 * there is more than one method to choose from.
 */
@Composable
internal fun TxFeeCell(
    feature: BaseConfirmFeature,
    tx: PendingTransaction,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
) {
    val pickedFeeId by feature.pickedFeeId.collectAsState()
    val committed by feature.txCommitted.collectAsState()
    val selected = remember(tx, pickedFeeId) {
        tx.fee.resolve(pickedFeeId)
    } ?: return
    val pickerAvailable = tx.fee.isPickerAvailable
    var showPicker by remember { mutableStateOf(false) }

    if (pickerAvailable) {
        FeeSelectorCell(
            value = selected.quotedValue(),
            symbol = selected.shortTitle(),
            enabled = !committed,
            onClick = { showPicker = true },
        )
    } else {
        FeeCell(fee = selected.formattedValue())
    }

    if (showPicker && !committed) {
        FeeMethodPickerSheet(
            options = tx.fee.options.map { it.toPickerOption() },
            selectedId = selected.id,
            onSelect = { id ->
                tx.fee.findOption(id)?.let(feature::selectFee)
            },
            onActionSelect = { id ->
                val option = tx.fee.findOption(id) ?: return@FeeMethodPickerSheet
                if (option.account is FeeAccount.Keeper) {
                    onOpenBattery(tx.wallet.id, BatteryNativeFrom.InsufficientFunds)
                }
            },
            onClose = { showPicker = false },
        )
    }
}

@Composable
internal fun PriceImpactCell(priceImpact: PriceImpact) {
    val impactColor = when (priceImpact.severity) {
        PriceImpactSeverity.Danger -> UIKit.colorScheme.accent.red
        PriceImpactSeverity.Warning -> UIKit.colorScheme.accent.orange
        PriceImpactSeverity.Normal -> UIKit.colorScheme.text.primary
    }
    val impactIcon = when (priceImpact.severity) {
        PriceImpactSeverity.Danger -> UIKitIcon.ic_exclamationmark_triangle_28
        PriceImpactSeverity.Warning -> UIKitIcon.ic_exclamationmark_circle_16
        PriceImpactSeverity.Normal -> null
    }
    MoonPropertyBigCell(
        title = {
            MoonPropertyTitle(
                title = stringResource(Localization.value_difference),
                infoTooltip = stringResource(Localization.value_difference_info),
            )
        },
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                impactIcon?.let {
                    MoonItemIcon(
                        painter = painterResource(it),
                        color = impactColor,
                        size = 16.dp,
                    )
                }
                MoonItemTitle(
                    text = priceImpact.text,
                    color = impactColor,
                )
            }
        },
    )
}

@Composable
internal fun PriceImpactWarningDialog(
    severity: PriceImpactSeverity,
    impact: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        PriceImpactWarningContent(
            severity = severity,
            impact = impact,
            onConfirm = {
                onConfirm()
                router.close()
            },
            onClose = { router.close() },
        )
    }
}

@Composable
internal fun ColumnScope.PriceImpactWarningContent(
    severity: PriceImpactSeverity,
    impact: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    MoonTopAppBarSimple(
        title = "",
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        backgroundColor = Color.Transparent,
    )

    Spacer(modifier = Modifier.height(20.dp))

    MoonItemIcon(
        painter = when (severity) {
            PriceImpactSeverity.Danger -> painterResource(UIKitIcon.ic_exclamationmark_triangle_colorful_84)
            else -> painterResource(UIKitIcon.ic_exclamationmark_circle_colorful_84)
        },
        color = Color.Unspecified,
        size = 66.dp,
    )

    MoonTextContentCell(
        title = stringResource(Localization.price_impact_too_high),
        description = stringResource(Localization.price_impact_too_high_description),
    )

    Spacer(modifier = Modifier.height(16.dp))

    MoonButtonCell(
        onClick = onConfirm,
        text = stringResource(Localization.swap_at_changed_price),
        colors = when (severity) {
            PriceImpactSeverity.Danger -> ButtonColorsRed
            else -> ButtonColorsOrange
        },
    )

    MoonButtonCell(
        onClick = onClose,
        text = stringResource(Localization.back_to_swap),
        colors = ButtonColorsSecondary,
    )

    Spacer(modifier = Modifier.height(16.dp))
}
