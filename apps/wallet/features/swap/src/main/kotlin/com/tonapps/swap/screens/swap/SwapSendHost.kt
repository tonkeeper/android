package com.tonapps.swap.screens.swap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.tonapps.deposit.multicoin.screens.confirm.HeadlessSwapConfirm
import com.tonapps.deposit.multicoin.screens.confirm.HeadlessSwapImpactDialog
import com.tonapps.deposit.multicoin.screens.confirm.HeadlessSwapPhase
import com.tonapps.deposit.multicoin.screens.confirm.HeadlessSwapPriceChangedDialog
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.errorText
import com.tonapps.deposit.multicoin.screens.confirm.rememberHeadlessSwapConfirm
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.filterNotNull

@Composable
internal fun SwapSendHost(
    attempt: SwapSend,
    feature: SwapFeature,
    current: MutableState<HeadlessSwapConfirm?>,
) {
    val confirm = rememberHeadlessSwapConfirm(
        request = attempt.request,
        review = attempt.phase == SwapSendPhase.Review,
        feeId = attempt.feeId,
    ) ?: return
    val update by confirm.updates.collectAsState()
    val amountBlocked by feature.amountBlocked.collectAsState()
    val sendFailed = stringResource(Localization.swap_send_failed)
    // A relayer's refusal says only that the swap was not sent: its reason is for the logs.
    val failure = when (val error = update.phase.error) {
        null, ConfirmationError.Unknown, is ConfirmationError.RelayerRejected -> sendFailed
        else -> error.errorText ?: sendFailed
    }
    val message = when (val phase = update.phase) {
        HeadlessSwapPhase.Requoted -> stringResource(Localization.swap_price_changed)
        is HeadlessSwapPhase.Retryable -> failure
        is HeadlessSwapPhase.Ended -> failure.takeIf { phase.error != null }
        is HeadlessSwapPhase.Blocked -> failure.takeIf {
            phase.error == ConfirmationError.PartiallySent || phase.error == ConfirmationError.NonceTaken
        }
        else -> null
    }

    DisposableEffect(confirm) {
        current.value = confirm
        onDispose {
            feature.sendAction(SwapAction.SendDisposed(attempt.id))
            if (current.value === confirm) {
                current.value = null
            }
        }
    }

    LaunchedEffect(confirm) {
        confirm.start()
        confirm.canConfirm.collect { feature.sendAction(SwapAction.Prepared(attempt.id, it)) }
    }

    LaunchedEffect(confirm) {
        confirm.feePlan.collect { feature.sendAction(SwapAction.FeePlanned(attempt.id, it)) }
    }

    LaunchedEffect(confirm) {
        confirm.feature.pickedFeeId.collect { feature.sendAction(SwapAction.FeePicked(attempt.id, it)) }
    }

    // A max its fee leaves no room for: the amount becomes what is left, and the route is quoted for it.
    LaunchedEffect(confirm) {
        confirm.refitAmount.filterNotNull().collect { feature.sendAction(SwapAction.FitMax(attempt.id, it)) }
    }

    LaunchedEffect(confirm, amountBlocked) {
        confirm.setAmountBlocked(amountBlocked)
    }

    LaunchedEffect(confirm, attempt.retries) {
        confirm.retry()
    }

    LaunchedEffect(confirm, attempt.phase) {
        if (attempt.phase == SwapSendPhase.Working) {
            confirm.confirmReview()
        }
    }

    LaunchedEffect(update) {
        val phase = update.phase

        // The amount that fits goes ahead of the progress that drops the attempt: it is what is quoted next.
        if (phase is HeadlessSwapPhase.Refit) {
            feature.sendAction(SwapAction.FitMax(attempt.id, phase.amount))
        }
        feature.sendAction(SwapAction.SendProgress(attempt.id, phase.toSendPhase(), message))

        // A max being fitted gives way as a stale route does, without the word about the price.
        if (phase == HeadlessSwapPhase.Requoted || phase is HeadlessSwapPhase.Refit) {
            feature.sendAction(SwapAction.Requote)
        }
    }

    HeadlessSwapImpactDialog(confirm)
    HeadlessSwapPriceChangedDialog(confirm)
}

private fun HeadlessSwapPhase.toSendPhase(): SwapSendPhase = when (this) {
    HeadlessSwapPhase.Working,
    is HeadlessSwapPhase.Impact,
    is HeadlessSwapPhase.PriceChanged -> SwapSendPhase.Working

    HeadlessSwapPhase.Done -> SwapSendPhase.Done
    is HeadlessSwapPhase.Retryable -> SwapSendPhase.Retryable
    is HeadlessSwapPhase.Blocked -> SwapSendPhase.Blocked
    HeadlessSwapPhase.Review -> SwapSendPhase.Review

    is HeadlessSwapPhase.Ended -> SwapSendPhase.Ended
    HeadlessSwapPhase.Requoted,
    is HeadlessSwapPhase.Refit -> SwapSendPhase.Requoted
}
