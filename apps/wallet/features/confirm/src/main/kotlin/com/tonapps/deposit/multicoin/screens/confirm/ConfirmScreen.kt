package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.deposit.multicoin.screens.confirm.battery.BatteryPurchaseConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.battery.BatteryPurchaseConfirmScreen
import com.tonapps.deposit.multicoin.screens.confirm.dapp.DappTxConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.dapp.DappTxConfirmScreen
import com.tonapps.deposit.multicoin.screens.confirm.dapp.confirmsAsDappOperation
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmScreen
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositConfirmScreen
import com.tonapps.deposit.multicoin.screens.confirm.transfer.TransferConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.transfer.TransferConfirmScreen
import com.tonapps.deposit.multicoin.screens.confirm.transfer.confirmsAsTransferOperation
import com.tonapps.log.L
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.CancellationException
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Entry point for the multichain confirm flow. Picks [SwapConfirmScreen] for swap requests,
 * [PerpetualDepositConfirmScreen] and [PerpetualConfirmScreen] for perps funding,
 * [TransferConfirmScreen] for the wallet's own sends and [DappTxConfirmScreen] for a dapp's EVM
 * transactions, each behind its flag and for a software wallet only, and [TxConfirmScreen] for other transfers, calls and message
 * signing, wiring up the matching feature.
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
    /** True while a battery purchase is being sent: its host keeps the screen open until it is over. */
    onBusyChange: (Boolean) -> Unit = {},
) {
    val signsOnHardware = if (request.confirmsAsTransferOperation() || request.confirmsAsDappOperation() || request.preparesSwapOperation()) {
        rememberIsHardwareWallet(request.data.walletId) ?: return
    } else {
        false
    }

    when (val type = request.type) {
        is ConfirmType.SwapOperation -> SwapOperationConfirm(request, onClose, onBack, onSendSuccess, onTopUp, onOpenBattery, rejectOnClose, onConfirm)

        // A route quoted on `/v3` is prepared into an operation and signed as one, unless a Ledger signs it.
        is ConfirmType.Swap -> if (!signsOnHardware && request.preparesSwapOperation()) {
            SwapOperationConfirm(request, onClose, onBack, onSendSuccess, onTopUp, onOpenBattery, rejectOnClose, onConfirm)
        } else {
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
                onOpenBattery = onOpenBattery,
            )
        }

        is ConfirmType.BatteryPurchase -> {
            val feature = koinViewModel<BatteryPurchaseConfirmFeature> { parametersOf(request) }
            BatteryPurchaseConfirmScreen(
                feature = feature,
                onBack = onBack,
                onClose = onClose,
                onSendSuccess = onSendSuccess,
                onBusyChange = onBusyChange,
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

        is ConfirmType.Transfer if !signsOnHardware && request.confirmsAsTransferOperation() -> {
            val feature = koinViewModel<TransferConfirmFeature> { parametersOf(request) }
            TransferConfirmScreen(
                feature = feature,
                onBack = onBack,
                onClose = onClose,
                onSendSuccess = onSendSuccess,
                onTopUp = onTopUp,
                onOpenBattery = onOpenBattery,
                onConfirm = onConfirm,
            )
        }

        // A dapp's transaction is signed as the dapp sent it.
        is ConfirmType.Transfer if !signsOnHardware && request.confirmsAsDappOperation() -> {
            DappTxConfirm(request, onClose, onBack, onSendSuccess, onTopUp, onOpenBattery, onConfirm, rejectOnClose)
        }

        is ConfirmType.Call if !signsOnHardware && request.confirmsAsDappOperation() -> {
            DappTxConfirm(request, onClose, onBack, onSendSuccess, onTopUp, onOpenBattery, onConfirm, rejectOnClose)
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

internal fun ConfirmRequest.preparesSwapOperation(): Boolean {
    return (type as? ConfirmType.Swap)?.quote?.genericPayload == true
}

@Composable
internal fun rememberIsHardwareWallet(walletId: String): Boolean? {
    val accountRepo = koinInject<McAccountRepository>()
    val isHardware by produceState<Boolean?>(initialValue = null, walletId) {
        value = try {
            accountRepo.getWallet(walletId)?.isHardware == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            true
        }
    }
    return isHardware
}

@Composable
private fun SwapOperationConfirm(
    request: ConfirmRequest,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    rejectOnClose: Boolean,
    onConfirm: () -> Unit,
) {
    val feature = koinViewModel<SwapOperationConfirmFeature> { parametersOf(request) }
    SwapOperationConfirmScreen(
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

@Composable
private fun DappTxConfirm(
    request: ConfirmRequest,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onConfirm: () -> Unit,
    rejectOnClose: Boolean,
) {
    val feature = koinViewModel<DappTxConfirmFeature> { parametersOf(request) }
    DappTxConfirmScreen(
        feature = feature,
        onClose = {
            if (rejectOnClose) {
                feature.reject()
            }
            onClose()
        },
        onBack = onBack,
        onSendSuccess = onSendSuccess,
        onTopUp = onTopUp,
        onOpenBattery = onOpenBattery,
        onConfirm = onConfirm,
    )
}
