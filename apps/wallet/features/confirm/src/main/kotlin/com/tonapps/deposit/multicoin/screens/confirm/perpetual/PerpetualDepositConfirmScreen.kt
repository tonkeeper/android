package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.core.components.chainLabel
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmContent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationProperty
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.localization.Localization
import ui.theme.UIKit

@Composable
fun PerpetualDepositConfirmScreen(
    feature: PerpetualDepositConfirmFeature,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    onSendSuccess: () -> Unit,
) {
    val deposit = feature.deposit
    val account by feature.sourceAccount.collectAsState()
    val asset = account?.asset

    OperationConfirmContent(
        feature = feature,
        modifier = Modifier.statusBarsPadding(),
        title = stringResource(Localization.perps_deposit_confirm_title, asset?.symbol.orEmpty()),
        titleDetails = asset?.chainLabel(),
        headerImage = asset,
        properties = account?.let { depositProperties(deposit, it) }.orEmpty(),
        onBack = onBack,
        onClose = onClose,
        onSendSuccess = onSendSuccess,
    ) {
        deposit.feeShareBps?.takeIf(::isHighFeeShare)?.let { feeShareBps ->
            Spacer(Modifier.height(12.dp))
            PerpetualHighFeeNote(feeShareBps)
        }

        Spacer(Modifier.height(12.dp))
        DepositNote(text = stringResource(Localization.perps_deposit_processing_note))
    }
}

@Composable
private fun depositProperties(deposit: ConfirmType.PerpetualDeposit, account: AccountWithDetails): List<OperationProperty> {
    val asset = account.asset.value
    val amount = asset.toDisplayUnit(deposit.amount)
    return listOfNotNull(
        OperationProperty(
            title = stringResource(Localization.perps_total_amount),
            value = Formatter.formatShort(value = amount, asset = asset),
            description = account.rate?.let { Formatter.formatFiat(value = amount, rate = it.value, separator = " ") },
        ),
        deposit.minReceive?.let {
            OperationProperty(title = stringResource(Localization.min_received), value = formatUsdc(it))
        },
        deposit.estimatedDurationSeconds?.let {
            OperationProperty(title = stringResource(Localization.perps_deposit_arrival_time), value = arrivalTimeValue(it))
        },
    )
}

@Composable
private fun DepositNote(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        text = text,
        style = UIKit.typography.body2,
        color = UIKit.colorScheme.text.tertiary,
        textAlign = TextAlign.Center,
    )
}

private fun formatUsdc(units: BigInteger): String = Formatter.formatFiat(
    value = USDC_DECIMALS.toDisplayUnit(units).value,
    currency = USD,
    trimTrailingZeros = false,
    options = Formatter.defaultOptions.copy(symbolSpacing = " "),
)

private val USDC_DECIMALS = Decimal(6)
private val USD = FiatCurrency("$")
