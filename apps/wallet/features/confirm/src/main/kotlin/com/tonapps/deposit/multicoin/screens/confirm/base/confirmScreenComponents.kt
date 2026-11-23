package com.tonapps.deposit.multicoin.screens.confirm

import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.tonapps.blockchain.model.DappConnectRequest.DomainStatus
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.core.LocalIsTopFragment
import com.tonapps.core.components.MoonDomainRiskBottomSheet
import com.tonapps.deposit.multicoin.screens.confirm.base.BaseTransactionConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmEvent
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.isRetryable
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.PrepareErrorMessage
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.rememberSliderState
import ui.components.moon.dialog.MoonAlertDialog
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.components.moon.container.MoonScaffold
import ui.moon.MoonToastHost
import ui.moon.rememberMoonToastHostState
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer
import uikit.widget.ToastFeedback

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
    feature: BaseTransactionConfirmFeature,
    title: String = "",
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
    val toastHost = rememberMoonToastHostState()
    val fallback = stringResource(Localization.sending_error)

    LaunchedEffect(confirmationError) {
        val rejected = confirmationError as? ConfirmationError.RelayerRejected ?: return@LaunchedEffect
        toastHost.showToast(rejected.message.ifBlank { fallback }, feedback = ToastFeedback.Failure)
    }

    // Coming back from the battery refill or receive flow must re-estimate. Navigation only adds
    // fragments on top, so this sheet never pauses — the top-fragment flag is the only signal.
    OnUncovered(feature::refreshFees)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        feature.events.flowWithLifecycle(lifecycle).collect {
            when (it) {
                ConfirmEvent.Done -> onSendSuccess()
            }
        }
    }

    // A transaction under re-preparation is not something to confirm yet, and its error belongs to the
    // attempt that is already gone: both wait until the new one lands.
    val tx = pendingTx.takeIf { !pendingLoader }
    val error = confirmationError.takeIf { !pendingLoader }

    Box(Modifier.fillMaxSize()) {
        ConfirmContent(
            feature = feature,
            title = title,
            tx = tx,
            sendingState = sendingState,
            error = error,
            onClose = onClose,
            onBack = onBack,
            onConfirm = onConfirm,
            onTopUp = onTopUp,
            footer = footer,
            content = content,
            onOpenBattery = onOpenBattery,
        )
        MoonToastHost(toastHost)
    }
}

@Composable
private fun ConfirmContent(
    feature: BaseTransactionConfirmFeature,
    title: String,
    tx: PendingTransaction?,
    sendingState: SendingState,
    error: ConfirmationError?,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
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
    var showDomainRiskWarning by remember { mutableStateOf(false) }

    val phase by rememberShimmerPhase()
    // Frozen while an error is up: a sweeping skeleton reads as "still loading" right above a slot
    // that says the opposite.
    val shimmer = when (error) {
        null -> phase
        else -> 0f
    }

    val priceImpact = tx?.priceImpact()
    val domainRisk = tx?.domainRisk()
    val pickedFeeId by feature.pickedFeeId.collectAsState()
    val selectedFee = remember(tx, pickedFeeId) {
        tx?.fee?.resolve(pickedFeeId)
    }
    val feeInsufficient = selectedFee != null && !TxFeeLogic.isSufficient(selectedFee)
    val feeQuoting by feature.feeQuoting.collectAsState()
    val mayHaveBeenSent = error == ConfirmationError.OutcomeUnknown ||
        error == ConfirmationError.PartiallySent ||
        error == ConfirmationError.NonceTaken
    val retryable = error?.isRetryable != false
    val feeUnavailable = tx?.isFeeUnavailable == true

    val sign: () -> Unit = {
        if (tx != null) {
            onConfirm()
            feature.sendTransaction(tx)
        }
    }
    val retry: () -> Unit = { feature.retry() }

    // Single thumb-reset path: covers a cancelled passcode dialog as well as a cleared error, and
    // keeps concurrent animateTo calls off the slider Animatable.
    LaunchedEffect(sendingState, error) {
        if (sendingState == SendingState.None && error == null && sliderState.isConfirmed) {
            sliderState.reset()
        }
    }

    MoonScaffold(
        modifier = Modifier.padding(
            bottom = navigationBarsBottomPadding(),
        ),
        title = title,
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
                val reason = error?.takeIf { feeUnavailable }?.feeErrorText
                if (reason != null) {
                    Spacer(Modifier.height(16.dp))
                    PrepareErrorMessage(reason)
                }
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
        val sliderThumbColor = when {
            domainRisk != null -> UIKit.colorScheme.accent.red
            impactWarning?.severity == PriceImpactSeverity.Danger -> UIKit.colorScheme.accent.red
            impactWarning?.severity == PriceImpactSeverity.Warning -> UIKit.colorScheme.accent.orange
            else -> UIKit.colorScheme.buttonPrimary.primaryBackground
        }
        val proceed: () -> Unit = {
            when {
                isMax == true -> showSendAllConfirmation = true
                impactWarning != null -> showPriceImpactWarning = true
                else -> sign()
            }
        }

        val domainRiskText = when (domainRisk) {
            DomainStatus.Scam -> stringResource(Localization.domain_status_scam)
            DomainStatus.Invalid -> stringResource(Localization.domain_status_invalid)
            DomainStatus.Valid,
            DomainStatus.Unknown,
            null -> null
        }
        if (domainRiskText != null) {
            Text(
                text = domainRiskText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 16.dp, end = 32.dp),
                color = UIKit.colorScheme.accent.red,
                style = UIKit.typography.body2,
                textAlign = TextAlign.Center,
            )
        }

        MoonSlideConfirmation(
            state = confirmationState,
            modifier = when {
                tx != null || error != null -> Modifier
                else -> Modifier.shimmer(shimmer)
            },
            sliderState = sliderState,
            error = error?.takeUnless { feeUnavailable }?.errorText,
            thumbColor = sliderThumbColor,
            title = when (confirmationState) {
                MoonSlideConfirmationState.Slider,
                MoonSlideConfirmationState.Loader -> stringResource(Localization.confirm)

                MoonSlideConfirmationState.Done -> stringResource(Localization.done)
            },
            subtitle = stringResource(Localization.swipe_right),
            buttonTitle = stringResource(Localization.try_again),
            enabled = tx != null && sendingState == SendingState.None && !feeInsufficient && !feeQuoting && !mayHaveBeenSent && !feeUnavailable,
            onConfirm = {
                when {
                    domainRisk != null -> showDomainRiskWarning = true
                    else -> proceed()
                }
            },
            onClick = retry.takeIf { retryable },
            onDone = {},
        )

        if (showDomainRiskWarning && domainRisk != null) {
            MoonDomainRiskBottomSheet(
                status = domainRisk,
                confirmText = stringResource(Localization.confirm_anyway),
                onConfirm = {
                    showDomainRiskWarning = false
                    proceed()
                },
                onClose = {
                    showDomainRiskWarning = false
                    scope.launch { sliderState.reset() }
                },
            )
        }

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
internal fun OnUncovered(action: () -> Unit) {
    val isTopFragment = LocalIsTopFragment.current
    var wasCovered by remember { mutableStateOf(false) }
    LaunchedEffect(isTopFragment) {
        if (!isTopFragment) {
            wasCovered = true
        } else if (wasCovered) {
            wasCovered = false
            action()
        }
    }
}

@Composable
internal fun FeeCell(
    fee: Pair<String, String?>,
    onClick: (() -> Unit)? = null,
    fiatFirst: Boolean,
    subtitle: String?,
    divider: Boolean = true,
) {
    val (value, detail) = when (fiatFirst) {
        true -> (fee.second ?: fee.first) to subtitle
        false -> fee
    }
    if (divider) {
        MoonItemDivider()
    }
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
                title = value,
                subtitle = detail,
            )
        },
        onClick = onClick,
    )
}

@Composable
fun TxFeeRow(
    feature: BaseTransactionConfirmFeature,
    tx: PendingTransaction,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    fiatFirst: Boolean = false,
    subtitle: String? = null,
) {
    if (tx.fee.options.isNotEmpty()) {
        TxFeeCell(
            feature = feature,
            tx = tx,
            onOpenBattery = onOpenBattery,
            fiatFirst = fiatFirst,
            subtitle = subtitle,
        )
        return
    }
    val fee = tx.fee()
    if (fee != null) {
        FeeCell(fee, fiatFirst = fiatFirst, subtitle = subtitle)
    } else if (tx.isFeeUnavailable) {
        UnavailableFeeCell(onRetry = { feature.retry() })
    }
}

@Composable
fun UnavailableFeeCell(onRetry: () -> Unit, divider: Boolean = true) {
    if (divider) {
        MoonItemDivider()
    }
    MoonPropertyBigCell(
        title = {
            MoonLargeItemSubtitle(
                text = stringResource(Localization.network_fee),
                color = UIKit.colorScheme.text.secondary,
            )
        },
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoonItemTitle(
                    text = stringResource(Localization.fee_unavailable),
                    color = UIKit.colorScheme.text.secondary,
                )
                MoonItemTitle(
                    text = stringResource(Localization.retry),
                    color = UIKit.colorScheme.text.accent,
                )
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_refresh_16),
                    color = UIKit.colorScheme.text.accent,
                )
            }
        },
        onClick = onRetry,
    )
}

/**
 * Fee row when there is a method to pick: value, then the paying asset as the tappable part.
 */
@Composable
internal fun FeeSelectorCell(
    value: String,
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    subtitle: String?,
    divider: Boolean = true,
) {
    val symbolColor = if (enabled) {
        UIKit.colorScheme.text.accent
    } else {
        UIKit.colorScheme.text.secondary
    }

    if (divider) {
        MoonItemDivider()
    }
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
                subtitle = subtitle,
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

@Composable
fun FeeQuotingCell(divider: Boolean = true) {
    val phase = rememberShimmerPhase()

    if (divider) {
        MoonItemDivider()
    }
    MoonPropertyBigCell(
        title = {
            MoonLargeItemSubtitle(
                text = stringResource(Localization.network_fee),
                color = UIKit.colorScheme.text.secondary,
            )
        },
        content = {
            Spacer(
                Modifier
                    .width(96.dp)
                    .height(20.dp)
                    .shimmer(phase)
            )
        },
    )
}

/**
 * Fee row for the multichain fee-option flows: renders the selected method and opens the picker when
 * there is more than one method to choose from.
 */
@Composable
internal fun TxFeeCell(
    feature: BaseTransactionConfirmFeature,
    tx: PendingTransaction,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    fiatFirst: Boolean,
    subtitle: String?,
) {
    val pickedFeeId by feature.pickedFeeId.collectAsState()
    val committed by feature.txCommitted.collectAsState()
    val quoting by feature.feeQuoting.collectAsState()
    val selected = remember(tx, pickedFeeId) {
        tx.fee.resolve(pickedFeeId)
    } ?: return
    var showPicker by remember { mutableStateOf(false) }

    when {
        quoting -> FeeQuotingCell()
        tx.fee.isPickerAvailable -> FeeSelectorCell(
            value = selected.quotedValue(),
            symbol = selected.shortTitle(),
            enabled = !committed,
            onClick = { showPicker = true },
            subtitle = subtitle,
        )
        else -> FeeCell(fee = selected.formattedValue(), fiatFirst = fiatFirst, subtitle = subtitle)
    }

    if (showPicker && !committed) {
        FeeMethodPickerSheet(
            options = tx.fee.pickerOptions.map { it.toPickerOption() },
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
fun PriceImpactCell(priceImpact: PriceImpact, onClick: (() -> Unit)? = null) {
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
        onClick = onClick,
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
    SwapWarningContent(
        danger = severity == PriceImpactSeverity.Danger,
        title = stringResource(Localization.price_impact_too_high),
        text = stringResource(Localization.price_impact_too_high_description),
        confirmText = stringResource(Localization.swap_at_changed_price),
        iconSize = 66.dp,
        onConfirm = onConfirm,
        onClose = onClose,
    )
}

@Composable
private fun navigationBarsBottomPadding(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var windowBottom by remember(view) { mutableIntStateOf(0) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            windowBottom = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                ?.bottom
                ?: 0
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    val composeBottom = WindowInsets.navigationBars.getBottom(density)
    return with(density) { maxOf(composeBottom, windowBottom).toDp() }
}
