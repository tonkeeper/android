package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmContent
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider

/**
 * The swap confirm of a `/v3` route, laid out as the transaction swap confirm: what is sent and
 * received, the route's rows and its fee. The swipe, the findings and the errors are the operation
 * confirm's.
 */
@Composable
fun SwapOperationConfirmScreen(
    feature: SwapOperationConfirmFeature,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onConfirm: () -> Unit = {},
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onTopUp: (assetId: String) -> Unit,
) {
    val quote by feature.quote.collectAsState()
    val sell by feature.account.collectAsState()
    val buy by feature.destination.collectAsState()
    val rateLabel by feature.rateLabel.collectAsState()
    val rateProgress by feature.swapQuoteCountdown.collectAsState()
    val slippage by feature.slippage.collectAsState()
    val selectedSlippageBps by feature.selectedSlippageBps.collectAsState()

    OperationConfirmContent(
        feature = feature,
        title = stringResource(Localization.confirm_swap),
        onBack = onBack,
        onClose = onClose,
        onSendSuccess = onSendSuccess,
        onTopUp = onTopUp,
        onOpenBattery = onOpenBattery,
        onConfirm = onConfirm,
        body = { feeRow ->
            val from = sell?.asset
            val to = buy?.asset
            val route = quote
            if (from != null && to != null && route != null) {
                val sent = remember(from, route.sourceBaseAmount) {
                    Formatter.formatShort(value = from.value.toDisplayUnit(route.sourceBaseAmount), asset = from.value)
                }
                val received = remember(to, route.buyBaseAmount) {
                    Formatter.formatShort(value = to.value.toDisplayUnit(route.buyBaseAmount), asset = to.value, approximate = true)
                }
                ConfirmSwapHeader(assetFrom = from, fromAmount = sent, assetTo = to, toAmount = received)
                Spacer(Modifier.height(16.dp))
            }

            val minReceived = remember(to, buy?.rate, route?.minimumBuyBaseAmount) {
                val asset = to?.value ?: return@remember null
                val amount = route?.minimumBuyBaseAmount ?: return@remember null
                formatMinReceived(asset = asset, rate = buy?.rate?.value, baseAmount = amount)
            }
            SwapRouteRows(
                rateLabel = rateLabel,
                rateProgress = rateProgress,
                minReceived = minReceived,
                slippage = slippage,
                selectedSlippageBps = selectedSlippageBps,
                onSelectSlippage = feature::selectSlippage,
                priceImpact = priceImpactOf(route?.priceImpactBps),
            ) {
                MoonItemDivider()
                feeRow()
            }
        },
    )
}
