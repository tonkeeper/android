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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.core.components.chainLabel
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerSheet
import com.tonapps.deposit.multicoin.screens.confirm.OnUncovered
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.NetworkFeeCell
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmContent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationProperty
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.shortTitle
import com.tonapps.deposit.multicoin.screens.confirm.toPickerOption
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.theme.UIKit

@Composable
fun PerpetualDepositConfirmScreen(
    feature: PerpetualDepositConfirmFeature,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    onSendSuccess: () -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
) {
    val deposit by feature.deposit.collectAsState()
    val quoteChanged by feature.quoteChanged.collectAsState()
    val account by feature.sourceAccount.collectAsState()
    val pricing by feature.pricing.collectAsState()
    val feeMethods by feature.feeMethods.collectAsState()
    val feeMethod by feature.feeMethod.collectAsState()
    val feeQuoting by feature.feeQuoting.collectAsState()
    val sendingState by feature.sendingState.collectAsState()
    val wallet by feature.wallet.collectAsState()
    val asset = account?.asset
    var showFeeMethods by remember { mutableStateOf(false) }
    val methodsSwitchable = feeMethods?.isPickerAvailable == true && sendingState == SendingState.None
    val battery = feeMethod?.takeIf { it.isBattery && !it.isUnpriced }

    OnUncovered { feature.sendAction(OperationConfirmAction.RefreshFees) }

    OperationConfirmContent(
        feature = feature,
        modifier = Modifier.statusBarsPadding(),
        title = stringResource(Localization.perps_deposit_confirm_title, asset?.symbol.orEmpty()),
        titleDetails = asset?.chainLabel(),
        headerImage = asset,
        properties = account?.let { depositProperties(deposit, it) }.orEmpty(),
        trailingProperties = batteryProperties(battery, pricing?.delivery, deposit.activatesAccount) +
            arrivalProperties(deposit),
        feeReady = pricing != null,
        feeRow = { fee, feeRate ->
            val fees = pricing?.fees
            val onSwitchMethod = if (methodsSwitchable) {
                { showFeeMethods = true }
            } else {
                null
            }
            when {
                battery != null -> PerpetualDepositFeeMethodCell(
                    title = stringResource(Localization.perps_deposit_network_fees),
                    value = stringResource(
                        Localization.perps_deposit_approx_value,
                        chargesText(battery.chargesAfterReserve.intValue(exactRequired = false)),
                    ),
                    symbol = battery.shortTitle(),
                    tooltip = null,
                    quoting = feeQuoting,
                    onSwitchMethod = onSwitchMethod,
                )

                fees != null -> PerpetualDepositFeesCell(
                    fees = fees,
                    activatesAccount = deposit.activatesAccount,
                    symbol = feeMethod?.shortTitle() ?: fees.symbol,
                    quoting = feeQuoting,
                    onSwitchMethod = onSwitchMethod,
                )

                else -> NetworkFeeCell(
                    fee = fee,
                    feeRate = feeRate,
                    infoTooltip = if (deposit.activatesAccount) {
                        stringResource(Localization.perps_deposit_account_setup_note)
                    } else {
                        null
                    },
                )
            }
        },
        onBack = onBack,
        onClose = onClose,
        onSendSuccess = onSendSuccess,
    ) {
        val highFeeShareBps = pricing?.feeShareBps?.takeIf(::isHighFeeShare)
        when {
            quoteChanged -> {
                Spacer(Modifier.height(12.dp))
                PerpetualQuoteChangedNote()
            }

            highFeeShareBps != null -> {
                Spacer(Modifier.height(12.dp))
                PerpetualHighFeeNote(highFeeShareBps)
            }
        }

        if (feature.delegatesAddress) {
            Spacer(Modifier.height(12.dp))
            DepositNote(text = stringResource(Localization.perps_deposit_delegation_notice, Chain.Ethereum.Mainnet.name))
        }

        Spacer(Modifier.height(12.dp))
        DepositNote(text = stringResource(Localization.perps_deposit_processing_note))
    }

    val methods = feeMethods
    if (showFeeMethods && methods != null && methodsSwitchable) {
        FeeMethodPickerSheet(
            options = methods.pickerOptions.map { it.toPickerOption() },
            selectedId = feeMethod?.id,
            onSelect = { id -> methods.findOption(id)?.let(feature::pickFee) },
            onActionSelect = { id ->
                val walletId = wallet?.id
                if (walletId != null && methods.findOption(id)?.isBattery == true) {
                    onOpenBattery(walletId, BatteryNativeFrom.InsufficientFunds)
                }
            },
            onClose = { showFeeMethods = false },
        )
    }
}

@Composable
private fun depositProperties(deposit: ConfirmType.PerpetualDeposit, account: AccountWithDetails): List<OperationProperty> {
    val asset = account.asset.value
    val amount = asset.toDisplayUnit(deposit.verifiedPayment?.amount ?: deposit.amount)
    return listOfNotNull(
        OperationProperty(
            title = stringResource(Localization.perps_deposit_you_send),
            value = Formatter.formatShort(value = amount, asset = asset),
            description = account.rate?.let { Formatter.formatFiat(value = amount, rate = it.value, separator = " ") },
        ),
        OperationProperty(
            title = stringResource(Localization.perps_deposit_to_perps_account),
            value = formatApproximateUsdc(deposit.quote.depositAmount),
        ),
        deposit.minReceive?.let {
            OperationProperty(title = stringResource(Localization.min_received), value = formatUsdc(it))
        },
        deposit.verifiedPayment?.let {
            OperationProperty(title = stringResource(Localization.provider), value = it.provider)
        },
    )
}

@Composable
private fun batteryProperties(
    battery: TxFee?,
    delivery: PerpetualDepositDeliveryFees?,
    activatesAccount: Boolean,
): List<OperationProperty> {
    battery ?: return emptyList()
    val reserve = battery.reserve?.intValue(exactRequired = false)?.takeIf { it > 0 }?.let { chargesText(it) }
    return listOfNotNull(
        delivery?.let {
            OperationProperty(
                title = stringResource(Localization.perps_deposit_fees),
                value = it.total,
                infoTooltip = perpetualDepositFeeBreakdown(it.network, it.provider, activatesAccount),
            )
        },
        reserve?.let {
            OperationProperty(
                title = stringResource(Localization.staking_temporary_reserve),
                value = stringResource(Localization.perps_deposit_approx_value, it),
                infoTooltip = stringResource(Localization.perps_deposit_temporary_reserve_info, it),
            )
        },
    )
}

@Composable
private fun arrivalProperties(deposit: ConfirmType.PerpetualDeposit): List<OperationProperty> {
    val seconds = deposit.estimatedDurationSeconds ?: return emptyList()
    return listOf(
        OperationProperty(
            title = stringResource(Localization.perps_deposit_arrival_time),
            value = arrivalTimeValue(seconds),
            infoTooltip = stringResource(Localization.perps_deposit_arrival_time_info),
        ),
    )
}

@Composable
private fun chargesText(charges: Int): String {
    return pluralStringResource(Plurals.battery_charges, charges, charges)
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
    options = usdFormatOptions(),
)

private fun formatApproximateUsdc(units: BigInteger): String = Formatter.formatFiat(
    value = USDC_DECIMALS.toDisplayUnit(units).value,
    currency = USD,
    approximate = true,
    options = usdFormatOptions(),
)

private val USDC_DECIMALS = Decimal(6)
