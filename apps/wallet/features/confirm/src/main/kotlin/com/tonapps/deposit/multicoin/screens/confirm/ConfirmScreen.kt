package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.runtime.Composable
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmScreen
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositConfirmScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Entry point for the multichain confirm flow. Picks [SwapConfirmScreen] for swap requests,
 * [PerpetualDepositConfirmScreen] and [PerpetualConfirmScreen] for perps funding, and
 * [TxConfirmScreen] for transfers, calls and message signing, wiring up the matching feature.
 *
 * @param rejectOnClose when true (dapp requests), rejecting the pending request before closing.
 * @param onTopUp opens the ramp for the given asset when the balance is not enough to send.
 * @param onConfirm invoked when the user commits the transaction, before it is signed and sent.
 */
@Composable
fun ConfirmScreen(
    request: ConfirmRequest,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    rejectOnClose: Boolean = false,
    onConfirm: () -> Unit = {},
) {
    when (request.type) {
        is ConfirmType.Swap -> {
            val feature = koinViewModel<SwapConfirmFeature> { parametersOf(request) }
            SwapConfirmScreen(
                feature = feature,
                onClose = {
                    if (rejectOnClose) {
                        feature.reject()
                    }
                    onClose()
                },
                onBack = onBack,
                onSendSuccess = onSendSuccess,
                onConfirm = onConfirm,
                onTopUp = onTopUp,
                onOpenBattery = onOpenBattery,
            )
        }

        is ConfirmType.PerpetualDeposit -> {
            val feature = koinViewModel<PerpetualDepositConfirmFeature> { parametersOf(request) }
            PerpetualDepositConfirmScreen(
                feature = feature,
                onBack = onBack,
                onClose = onClose,
                onSendSuccess = onSendSuccess,
            )
        }

        is ConfirmType.PerpetualWithdraw -> {
            val feature = koinViewModel<PerpetualConfirmFeature> { parametersOf(request) }
            PerpetualConfirmScreen(
                feature = feature,
                onBack = onBack,
                onClose = onClose,
                onSendSuccess = onSendSuccess,
            )
        }

        is ConfirmType.Transfer,
        is ConfirmType.Call,
        is ConfirmType.Message -> {
            val feature = koinViewModel<TxConfirmFeature> { parametersOf(request) }
            TxConfirmScreen(
                feature = feature,
                onClose = {
                    if (rejectOnClose) {
                        feature.reject()
                    }
                    onClose()
                },
                onBack = onBack,
                onSendSuccess = onSendSuccess,
                onConfirm = onConfirm,
                onTopUp = onTopUp,
                onOpenBattery = onOpenBattery,
            )
        }
    }
}
