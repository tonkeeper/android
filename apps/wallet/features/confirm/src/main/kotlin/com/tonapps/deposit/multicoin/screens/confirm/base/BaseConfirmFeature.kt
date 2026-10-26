package com.tonapps.deposit.multicoin.screens.confirm.base

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

sealed interface ConfirmationError {
    data object Unknown : ConfirmationError

    data object OutcomeUnknown : ConfirmationError
    data object InsufficientBalance : ConfirmationError

    data object Expired : ConfirmationError

    data object InvalidAddress : ConfirmationError
    data object UnsupportedTransaction : ConfirmationError
    data object UnsupportedAsset : ConfirmationError
    data object MissingField : ConfirmationError
    data object DustAmount : ConfirmationError
    data object VerificationFailed : ConfirmationError
    data object SignInternalError : ConfirmationError

    data object Unauthorized : ConfirmationError
    data object NoAvailableNodes : ConfirmationError
    data object NetworkError : ConfirmationError

    data object BadResponse : ConfirmationError
    data object BitcoinError : ConfirmationError
    data object MemPoolConflict : ConfirmationError
    data object UtxoError : ConfirmationError
    data object InternalError : ConfirmationError

    data class NodeRejected(val message: String) : ConfirmationError

    data class RelayerRejected(val message: String) : ConfirmationError

    data object DepositInFlight : ConfirmationError
}

interface ConfirmationErrorCause {
    val confirmationError: ConfirmationError
}

enum class SendingState {
    None, Loading, Done
}

sealed interface ConfirmEvent {
    object Done : ConfirmEvent
}

internal const val SUCCESS_HOLD_MS = 1_500L

/**
 * What every multichain confirm feature shares: the request it confirms, the send state, the error
 * its action slot shows and the event that closes the screen. [BaseTransactionConfirmFeature] binds
 * the request to a transaction; [com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature] signs the request's operation as it is.
 */
abstract class BaseConfirmFeature<State : MviViewState, Action : MviAction>(
    protected val request: ConfirmRequest,
    protected val accountRepo: McAccountRepository,
    protected val passcodeManager: PasscodeManager,
) : GraphViewModel<State, Action>() {

    private val relay = MviRelay<ConfirmEvent>()
    val events = relay.events

    abstract val confirmationError: StateFlow<ConfirmationError?>

    abstract val sendingState: StateFlow<SendingState>

    protected fun finish() {
        relay.emit(ConfirmEvent.Done)
    }

    protected suspend fun unlockCryptoWallet(context: Context, walletId: String): CryptoWallet = withContext(Async.Io) {
        passcodeManager.unlockMultichainVault(context) { coder ->
            accountRepo.getCryptoWallet(walletId, coder)
                ?: throw IllegalStateException("CryptoWallet not found for $walletId")
        }
    }

    protected fun parseSignError(error: SignError): ConfirmationError = when (error) {
        is SignError.DustAmount -> ConfirmationError.DustAmount
        is SignError.InsufficientInputs -> ConfirmationError.InsufficientBalance
        is SignError.InternalError -> ConfirmationError.SignInternalError
        is SignError.InvalidAddress -> ConfirmationError.InvalidAddress
        is SignError.MissingField -> ConfirmationError.MissingField
        is SignError.Unknown -> ConfirmationError.Unknown
        is SignError.UnsupportedAsset -> ConfirmationError.UnsupportedAsset
        is SignError.UnsupportedTransaction -> ConfirmationError.UnsupportedTransaction
        is SignError.VerificationFailed -> ConfirmationError.VerificationFailed
    }

    protected fun parseNodeError(error: NodeError): ConfirmationError = when (error) {
        is NodeError.InsufficientFunds -> ConfirmationError.InsufficientBalance
        is NodeError.NetworkWrap -> ConfirmationError.NetworkError
        is NodeError.NoAvailableNodes -> ConfirmationError.NoAvailableNodes
        is NodeError.Unauthorized -> ConfirmationError.Unauthorized
        is NodeError.Rejected -> ConfirmationError.NodeRejected(error.message)
        is NodeError.Unknown -> ConfirmationError.Unknown
    }

    protected fun parseChainError(error: ChainError): ConfirmationError = when (error) {
        is ChainError.BadResponse -> ConfirmationError.BadResponse
        is ChainError.BitcoinDustError -> ConfirmationError.DustAmount
        is ChainError.BitcoinError -> ConfirmationError.BitcoinError
        is ChainError.BitcoinInsufficientInputs -> ConfirmationError.InsufficientBalance
        is ChainError.BitcoinMemPoolConflict -> ConfirmationError.MemPoolConflict
        is ChainError.InsufficientFunds -> ConfirmationError.InsufficientBalance
        is ChainError.InternalError -> ConfirmationError.InternalError
        is ChainError.Unknown -> ConfirmationError.Unknown
        is ChainError.UtxoError -> ConfirmationError.UtxoError
    }
}
