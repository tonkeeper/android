package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationImage
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.rememberSliderState
import ui.components.moon.container.MoonScaffold
import ui.theme.UIKit

@Composable
fun PerpetualConfirmScreen(
    feature: PerpetualConfirmFeature,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    onSendSuccess: () -> Unit,
) {
    val review by feature.review.collectAsState()
    val loading by feature.loading.collectAsState()
    val loadError by feature.loadError.collectAsState()
    val sending by feature.sending.collectAsState()
    val submitError by feature.submitError.collectAsState()
    val context = LocalContext.current
    val sliderState = rememberSliderState()

    val error = submitError ?: loadError

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                PerpetualConfirmEvent.Submitted -> onSendSuccess()
            }
        }
    }

    LaunchedEffect(sending, error) {
        if (sending == PerpetualSendingState.None && error == null && sliderState.isConfirmed) {
            sliderState.reset()
        }
    }

    MoonScaffold(
        modifier = Modifier
            .statusBarsPadding()
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        title = "",
        onBack = onBack,
        onClose = onClose,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shown = review
            val failure = loadError
            when {
                shown != null -> ConfirmBody(display = shown.display)
                loading -> MoonLoaderCell(height = 320.dp)
                failure != null -> LoadErrorMessage(errorText(failure))
            }
        }

        val confirmationState = when (sending) {
            PerpetualSendingState.None -> MoonSlideConfirmationState.Slider
            PerpetualSendingState.Loading -> MoonSlideConfirmationState.Loader
            PerpetualSendingState.Done -> MoonSlideConfirmationState.Done
        }
        val blocking = error != null && !error.isRetryable
        MoonSlideConfirmation(
            state = confirmationState,
            sliderState = sliderState,
            error = error?.let { errorText(it) },
            title = stringResource(
                if (confirmationState == MoonSlideConfirmationState.Done) {
                    Localization.done
                } else {
                    Localization.confirm
                },
            ),
            subtitle = stringResource(Localization.swipe_right),
            buttonTitle = stringResource(Localization.try_again),
            enabled = review != null && !loading && loadError == null && sending == PerpetualSendingState.None && !blocking,
            onConfirm = { feature.confirm(context) },
            onClick = { feature.retry(context) },
            onDone = {},
        )
    }
}

@Composable
private fun LoadErrorMessage(text: String) {
    Spacer(Modifier.height(48.dp))
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        text = text,
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.accent.red,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ConfirmBody(display: PerpetualReview.Display) {
    Spacer(Modifier.height(16.dp))
    OperationImage(image = display.image, size = 96.dp)
    Spacer(Modifier.height(20.dp))
    MoonItemSubtitle(
        text = stringResource(Localization.confirm_action),
        color = UIKit.colorScheme.text.secondary,
    )
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Localization.perps_withdraw_confirm_title, display.symbol),
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
        )
        display.network?.let { network ->
            Text(
                text = network,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.secondary,
            )
        }
    }
    Spacer(Modifier.height(32.dp))

    when (display) {
        is PerpetualReview.Withdraw -> {
            WithdrawRows(display)
            display.feeShareBps?.takeIf(::isHighFeeShare)?.let { feeShareBps ->
                Spacer(Modifier.height(12.dp))
                PerpetualHighFeeNote(feeShareBps)
            }
        }
    }
}

@Composable
private fun WithdrawRows(display: PerpetualReview.Withdraw) {
    MoonBundleCell {
        Column {
            ConfirmRow {
                MoonPropertyBigCell(
                    title = stringResource(Localization.perps_withdraw_you_withdraw),
                    value = display.amount,
                    valueDescription = null,
                )
            }
            MoonItemDivider()
            ConfirmRow {
                MoonPropertyBigCell(
                    title = stringResource(Localization.perps_withdraw_you_receive),
                    value = display.receive,
                    valueDescription = null,
                )
            }
            MoonItemDivider()
            ConfirmRow {
                val fee = display.fee
                MoonPropertyBigCell(
                    title = stringResource(Localization.network_fee),
                    value = when {
                        fee == null -> stringResource(Localization.perps_withdraw_fee_free)
                        display.feeIsEstimate -> stringResource(Localization.perps_withdraw_fee_estimate, fee)
                        else -> fee
                    },
                    valueDescription = null,
                )
            }
            display.arrivalSeconds?.let { seconds ->
                MoonItemDivider()
                ConfirmRow {
                    MoonPropertyBigCell(
                        title = stringResource(Localization.perps_deposit_arrival_time),
                        value = arrivalTimeValue(seconds),
                        valueDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.heightIn(min = CONFIRM_ROW_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun errorText(error: PerpetualConfirmError): String {
    return when (error) {
        is PerpetualConfirmError.BelowMinimum -> if (error.minAmount != null) {
            stringResource(Localization.perps_withdraw_min_amount, error.minAmount)
        } else {
            stringResource(Localization.perps_withdraw_amount_too_low)
        }

        PerpetualConfirmError.InsufficientBalance -> stringResource(Localization.insufficient_balance)
        PerpetualConfirmError.InFlight -> stringResource(Localization.perps_withdraw_in_flight)
        PerpetualConfirmError.AccountUnavailable -> stringResource(Localization.perps_withdraw_account_unavailable)
        PerpetualConfirmError.ServiceUnavailable -> stringResource(Localization.perps_service_unavailable)
        PerpetualConfirmError.Refused -> stringResource(Localization.perps_withdraw_refused)
        PerpetualConfirmError.QuoteExpired -> stringResource(Localization.perps_deposit_quote_expired)
        PerpetualConfirmError.PrepareFailed -> stringResource(Localization.perps_withdraw_prepare_failed)
        PerpetualConfirmError.AmountTooLow -> stringResource(Localization.perps_withdraw_amount_too_low)
        PerpetualConfirmError.Failed -> stringResource(Localization.perps_withdraw_failed)
    }
}

private val CONFIRM_ROW_HEIGHT = 56.dp
