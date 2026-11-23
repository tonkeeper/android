package com.tonapps.deposit.multicoin.screens.confirm.battery

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.core.components.chainLabel
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationImage
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.components.moon.MoonItemDivider
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.rememberSliderState
import ui.components.moon.container.MoonScaffold
import ui.theme.UIKit

@Composable
fun BatteryPurchaseConfirmScreen(
    feature: BatteryPurchaseConfirmFeature,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    onSendSuccess: () -> Unit,
    onBusyChange: (Boolean) -> Unit = {},
) {
    val review by feature.review.collectAsState()
    val account by feature.account.collectAsState()
    val loading by feature.loading.collectAsState()
    val loadError by feature.loadError.collectAsState()
    val sending by feature.sending.collectAsState()
    val submitError by feature.submitError.collectAsState()
    val sliderState = rememberSliderState()

    val error = submitError ?: loadError

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                BatteryPurchaseEvent.Sent -> onSendSuccess()
            }
        }
    }

    // A purchase being sent is seen through: closing now would lose the news that it went out.
    val busy = sending != BatteryPurchaseSendingState.None
    LaunchedEffect(busy) {
        onBusyChange(busy)
    }

    LaunchedEffect(sending, error) {
        if (sending == BatteryPurchaseSendingState.None && error == null && sliderState.isConfirmed) {
            sliderState.reset()
        }
    }

    MoonScaffold(
        modifier = Modifier
            .statusBarsPadding()
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        title = "",
        onBack = onBack?.let { back ->
            {
                if (!busy) {
                    back()
                }
            }
        },
        onClose = {
            if (!busy) {
                onClose()
            }
        },
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
                shown != null -> ConfirmBody(feature.purchase.symbol, shown, account)
                loading -> MoonLoaderCell(height = 320.dp)
                failure != null -> LoadErrorMessage(errorText(failure))
            }
        }

        val confirmationState = when (sending) {
            BatteryPurchaseSendingState.None -> MoonSlideConfirmationState.Slider
            BatteryPurchaseSendingState.Loading -> MoonSlideConfirmationState.Loader
            BatteryPurchaseSendingState.Done -> MoonSlideConfirmationState.Done
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
            enabled = review != null && !loading && loadError == null && sending == BatteryPurchaseSendingState.None && !blocking,
            onConfirm = { feature.confirm() },
            // An error that must not be retried leaves only its message: no "Try again" to tap.
            onClick = { feature.retry() }.takeUnless { blocking },
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
private fun ConfirmBody(symbol: String, review: BatteryPurchaseReview, account: AccountWithDetails?) {
    OperationImage(image = account?.asset, size = 96.dp)
    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(Localization.confirm_action),
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.text.secondary,
    )
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Localization.battery_refill_crypto, symbol),
            style = UIKit.typography.h3,
            color = UIKit.colorScheme.text.primary,
        )
        account?.asset?.chainLabel()?.let { network ->
            Text(
                text = network,
                style = UIKit.typography.h3,
                color = UIKit.colorScheme.text.tertiary,
            )
        }
    }
    Spacer(Modifier.height(32.dp))
    PurchaseRows(symbol, review, account)
}

@Composable
private fun PurchaseRows(symbol: String, review: BatteryPurchaseReview, account: AccountWithDetails?) {
    MoonBundleCell {
        Column {
            ConfirmRow {
                MoonPropertyBigCell(
                    title = stringResource(Localization.battery_purchase_you_pay),
                    value = paidText(symbol, review, account),
                    valueDescription = account?.rate?.let { rate ->
                        val asset = account.asset.value
                        val amount = asset.toDisplayUnit(BigInteger.parseString(review.quote.amount.toString()))
                        Formatter.formatFiat(value = amount, rate = rate.value, separator = " ")
                    },
                )
            }
            MoonItemDivider()
            ConfirmRow {
                MoonPropertyBigCell(
                    title = stringResource(Localization.battery_purchase_you_get),
                    value = chargesText(review.quote.charges),
                    valueDescription = null,
                )
            }
            MoonItemDivider()
            ConfirmRow {
                MoonPropertyBigCell(
                    title = stringResource(Localization.network_fee),
                    value = chargesText(review.quote.feeCharges),
                    valueDescription = stringResource(Localization.battery_purchase_network_fee_caption),
                )
            }
        }
    }
}

@Composable
private fun paidText(symbol: String, review: BatteryPurchaseReview, account: AccountWithDetails?): String {
    val asset = account?.asset?.value
    if (asset != null) {
        val amount = asset.toDisplayUnit(BigInteger.parseString(review.quote.amount.toString()))
        return Formatter.formatShort(value = amount, asset = asset)
    }
    return "${review.quote.amount} $symbol"
}

@Composable
private fun chargesText(charges: Long): String {
    val count = charges.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(Plurals.battery_charges, count, String.format(CurrencyFormatter.locale, "%d", charges))
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
private fun errorText(error: BatteryPurchaseError): String {
    return when (error) {
        is BatteryPurchaseError.Rejected -> error.message?.takeIf { it.isNotBlank() } ?: stringResource(Localization.sending_error)
        BatteryPurchaseError.Expired -> stringResource(Localization.perps_deposit_quote_expired)
        BatteryPurchaseError.ChargesChanged -> stringResource(Localization.battery_purchase_charges_changed)
        BatteryPurchaseError.OutcomeUnknown -> stringResource(Localization.battery_purchase_outcome_unknown)
        BatteryPurchaseError.NoCharges -> stringResource(Localization.battery_purchase_no_charges)
        BatteryPurchaseError.Failed -> stringResource(Localization.sending_error)
    }
}

private val CONFIRM_ROW_HEIGHT = 56.dp
